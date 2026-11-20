package com.torturemod.feature.minions;

import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.help.Hints;
import com.torturemod.api.model.Area;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Menus;
import com.torturemod.api.port.Minions;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.EscapeHelped;
import com.torturemod.api.prison.PrisonEscaped;
import com.torturemod.api.prison.PrisonInfo;
import com.torturemod.api.prison.PrisonRight;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.torture.MenuButton;
import com.torturemod.api.util.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Мобы тюрем: подчинение, режимы, охрана, поручения курьерам и фермерам, раздел пульта. */
final class Army {
    static final String COLLECTION = "minions";
    private static final int THINK_INTERVAL_TICKS = 10;
    private static final double REACH = 2.5;
    private static final long CAPTIVE_WAIT_MILLIS = 5 * 60_000L;

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final ResistanceService resistance;
    private final PrisonService prisons;
    private final DoubleSupplier random;
    final Map<TargetId, Minion> minions = new LinkedHashMap<>();
    private final Map<TargetId, Job> jobs = new HashMap<>();
    /** Тюрьма → кого охрана атакует и до какого времени: беглецы и пособники. */
    private final Map<String, Map<TargetId, Long>> hostiles = new HashMap<>();
    private long ticks;

    Army(FeatureContext context, CaptivityService captivity, ResistanceService resistance, PrisonService prisons,
         DoubleSupplier random) {
        this.context = context;
        this.captivity = captivity;
        this.resistance = resistance;
        this.prisons = prisons;
        this.random = random;
    }

    // ---------- хранение ----------

    void load() {
        context.storage().loadDocuments(COLLECTION).join().forEach((key, json) -> {
            try {
                Map<String, Object> in = Json.readObject(json);
                String prisonId = String.valueOf(in.get("prison"));
                if (prisons.prison(prisonId).isEmpty()) {
                    context.storage().deleteDocument(COLLECTION, key);
                    return;
                }
                @SuppressWarnings("unchecked")
                List<Object> post = (List<Object>) in.get("post");
                Minion minion = new Minion(new TargetId(UUID.fromString(key)), String.valueOf(in.get("type")), prisonId,
                        new TargetId(UUID.fromString(String.valueOf(in.get("owner")))),
                        Minion.Mode.valueOf(String.valueOf(in.get("mode"))),
                        new Location(String.valueOf(post.get(0)), num(post.get(1)), num(post.get(2)), num(post.get(3))));
                if (in.get("carrying") instanceof List<?> list) {
                    for (Object item : list) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        minion.carrying.add(new ItemStackData(String.valueOf(map.get("id")), (int) num(map.get("count")),
                                String.valueOf(map.get("data"))));
                    }
                }
                minions.put(minion.id, minion);
            } catch (RuntimeException e) {
                context.platform().log().error("Cannot read minion " + key, e);
            }
        });
    }

    private void save(Minion minion) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", minion.typeId);
        out.put("prison", minion.prisonId);
        out.put("owner", minion.owner.value().toString());
        out.put("mode", minion.mode.name());
        out.put("post", List.of(minion.post.worldId(), minion.post.x(), minion.post.y(), minion.post.z()));
        List<Object> carrying = new ArrayList<>();
        for (ItemStackData stack : minion.carrying) {
            carrying.add(Map.of("id", stack.itemId(), "count", stack.count(), "data", stack.data()));
        }
        out.put("carrying", carrying);
        context.storage().saveDocument(COLLECTION, minion.id.value().toString(), Json.write(out))
                .exceptionally(this::fail);
    }

    private void remove(Minion minion) {
        minions.remove(minion.id);
        jobs.remove(minion.id);
        context.storage().deleteDocument(COLLECTION, minion.id.value().toString()).exceptionally(this::fail);
    }

    private Void fail(Throwable error) {
        context.platform().log().error("Minion storage failed", error);
        return null;
    }

    // ---------- подчинение ----------

    void onInteract(GameEvent.EntityInteracted event) {
        if (event.cancellation().isCancelled() || captivity.isCaptive(event.actor())) {
            return;
        }
        Minion minion = minions.get(event.target());
        if (minion != null) {
            interactWithMinion(event, minion);
            return;
        }
        if (event.itemId().filter(controlItem()::equals).isEmpty()) {
            return;
        }
        Optional<Captive> captive = captivity.find(event.target()).filter(found -> found.kind() == TargetKind.MOB);
        Optional<TargetState> state = context.platform().targets().state(event.target());
        if (captive.isEmpty() || state.isEmpty()) {
            return;
        }
        event.cancellation().cancel();
        Optional<PrisonInfo> prison = prisons.prisonOf(event.target()).flatMap(prisons::prison)
                .or(() -> prisons.prisonAt(state.get().location()));
        if (prison.isEmpty() || !prisons.hasRight(prison.get().id(), event.actor(), PrisonRight.ARMY)) {
            bar(event.actor(), text("minions.control.not_here"));
            return;
        }
        if (!resistance.isBroken(event.target())) {
            bar(event.actor(), text("minions.control.not_broken", state.get().name()));
            return;
        }
        context.platform().items().consume(event.actor(), controlItem(), 1);
        enlist(event.target(), state.get(), prison.get(), event.actor());
    }

    /** Сломленный голем в тюрьме служит ей сам: ему не нужен контроль разума. */
    void onBroken(TargetId captive) {
        Optional<TargetState> state = context.platform().targets().state(captive);
        if (state.isEmpty() || state.get().kind() != TargetKind.MOB || !golemTypes().contains(state.get().typeId())) {
            return;
        }
        Optional<PrisonInfo> prison = prisons.prisonOf(captive).flatMap(prisons::prison)
                .or(() -> prisons.prisonAt(state.get().location()));
        if (prison.isEmpty()) {
            return;
        }
        TargetId owner = captivity.find(captive).flatMap(Captive::captor)
                .filter(captor -> prisons.hasRight(prison.get().id(), captor, PrisonRight.ARMY))
                .orElse(prison.get().owner());
        enlist(captive, state.get(), prison.get(), owner);
    }

    private void enlist(TargetId mob, TargetState state, PrisonInfo prison, TargetId owner) {
        captivity.release(mob, ReleaseReason.SERVICE);
        Minion minion = new Minion(mob, state.typeId(), prison.id(), owner, Minion.Mode.POST, state.location());
        minions.put(mob, minion);
        minions().enslave(mob);
        context.platform().targets().setPersistent(mob);
        minions().setLabel(mob, label(minion, prison));
        context.platform().feedback().particles(state.location(), "minecraft:portal", 40);
        context.platform().feedback().sound(state.location(), "minecraft:entity.ender_eye.death");
        save(minion);
        context.platform().feedback().message(owner, text("minions.enlisted", state.name(), prison.name()));
        context.services().find(Hints.class).ifPresent(hints -> hints.hint(owner, "hint.minions.enlisted"));
    }

    private void interactWithMinion(GameEvent.EntityInteracted event, Minion minion) {
        boolean friend = prisons.isFriend(minion.prisonId, event.actor());
        if (!friend) {
            return;
        }
        boolean army = prisons.hasRight(minion.prisonId, event.actor(), PrisonRight.ARMY);
        if (event.sneaking() && event.itemId().isEmpty() && army
                && context.platform().items().heldItem(event.actor()).isEmpty()) {
            event.cancellation().cancel();
            cycleMode(event.actor(), minion);
            return;
        }
        if (minion.isVillager()) {
            // Торговля идёт как обычно, но дешевле: ванильная репутация растёт с каждым разговором.
            minions().befriend(minion.id, event.actor());
            return;
        }
        if (army && event.itemId().isPresent() && !event.sneaking()
                && context.platform().items().heldItem(event.actor()).equals(event.itemId())
                && minions().equipFromHand(event.actor(), minion.id)) {
            event.cancellation().cancel();
            bar(event.actor(), text("minions.equipped", name(minion.id)));
        }
    }

    private void cycleMode(TargetId actor, Minion minion) {
        Minion.Mode[] modes = Minion.Mode.values();
        minion.mode = modes[(minion.mode.ordinal() + 1) % modes.length];
        if (minion.mode == Minion.Mode.FOLLOW) {
            minion.owner = actor;
        }
        context.platform().targets().state(minion.id).ifPresent(state -> {
            if (minion.mode == Minion.Mode.POST) {
                minion.post = state.location();
            }
        });
        jobs.remove(minion.id);
        minions().setAttackTarget(minion.id, Optional.empty());
        prisons.prison(minion.prisonId).ifPresent(prison -> minions().setLabel(minion.id, label(minion, prison)));
        save(minion);
        bar(actor, text("minions.mode." + minion.mode.name(), name(minion.id)));
    }

    // ---------- враги охраны ----------

    void onEscaped(PrisonEscaped event) {
        mark(event.prisonId(), event.captive(), context.config().getInt("minions.chase_escapee_seconds", 120));
    }

    void onHelped(EscapeHelped event) {
        mark(event.prisonId(), event.helper(), context.config().getInt("minions.chase_helper_seconds", 60));
    }

    void onDamaged(GameEvent.Damaged event) {
        Minion minion = minions.get(event.target());
        if (minion != null && event.source().isPresent() && !prisons.isFriend(minion.prisonId, event.source().get())
                && !minions.containsKey(event.source().get())) {
            mark(minion.prisonId, event.source().get(), context.config().getInt("minions.chase_helper_seconds", 60));
        }
    }

    private void mark(String prisonId, TargetId target, int seconds) {
        hostiles.computeIfAbsent(prisonId, key -> new HashMap<>())
                .put(target, context.clock().millis() + Math.max(1, seconds) * 1000L);
    }

    void onDied(TargetId target) {
        Minion minion = minions.get(target);
        if (minion != null) {
            if (!minion.carrying.isEmpty()) {
                minions().drop(minion.id, List.copyOf(minion.carrying));
            }
            remove(minion);
            prisons.prison(minion.prisonId).ifPresent(prison -> notifyArmy(prison.id(), "minions.died",
                    minion.typeId, prison.name()));
        }
        hostiles.values().forEach(map -> map.remove(target));
    }

    // ---------- PrisonGuards ----------

    boolean anyGuardSees(String prisonId, TargetId target, double maxDistance) {
        for (Minion minion : minions.values()) {
            if (!minion.prisonId.equals(prisonId) || minion.isVillager()) {
                continue;
            }
            Optional<TargetState> guard = context.platform().targets().state(minion.id);
            Optional<TargetState> seen = context.platform().targets().state(target);
            if (guard.isPresent() && seen.isPresent()
                    && distance(guard.get().location(), seen.get().location()) <= maxDistance
                    && context.platform().targets().canSee(minion.id, target)) {
                return true;
            }
        }
        return false;
    }

    // ---------- PrisonCouriers ----------

    boolean deliver(String prisonId, TargetId captive, BlockLocation fromChest, List<ItemStackData> items) {
        Optional<Minion> courier = minions.values().stream()
                .filter(minion -> minion.prisonId.equals(prisonId) && minion.mode == Minion.Mode.COURIER)
                .filter(minion -> !jobs.containsKey(minion.id) && minion.carrying.isEmpty())
                .filter(minion -> context.platform().targets().state(minion.id).isPresent())
                .findFirst();
        if (courier.isEmpty()) {
            return false;
        }
        jobs.put(courier.get().id, new Job(Job.Kind.DELIVER, Optional.of(fromChest), Optional.empty(), Optional.of(captive),
                items, Job.Stage.TO_SOURCE, context.clock().millis()));
        return true;
    }

    // ---------- тик ----------

    void tick() {
        if (++ticks % THINK_INTERVAL_TICKS != 0) {
            return;
        }
        long now = context.clock().millis();
        hostiles.values().forEach(map -> map.values().removeIf(until -> until < now));
        for (Minion minion : List.copyOf(minions.values())) {
            Optional<TargetState> state = context.platform().targets().state(minion.id);
            Optional<PrisonInfo> prison = prisons.prison(minion.prisonId);
            if (prison.isEmpty()) {
                minions().free(minion.id);
                minions().setLabel(minion.id, "");
                remove(minion);
                continue;
            }
            if (state.isEmpty()) {
                continue;
            }
            minions().enslave(minion.id);
            minions().setLabel(minion.id, label(minion, prison.get()));
            if (minion.isVillager()) {
                farm(minion, state.get(), prison.get());
                continue;
            }
            if (minion.mode != Minion.Mode.COURIER && guard(minion, state.get(), prison.get())) {
                continue;
            }
            switch (minion.mode) {
                case POST -> patrol(minion, state.get(), prison.get());
                case FOLLOW -> follow(minion, state.get());
                case COURIER -> courier(minion, state.get(), prison.get());
            }
        }
    }

    /** @return занят ли моб боем */
    private boolean guard(Minion minion, TargetState self, PrisonInfo prison) {
        double range = context.config().getDouble("minions.guard_range", 16);
        double margin = context.config().getDouble("minions.post_margin", 16);
        Optional<TargetState> target = hostiles.getOrDefault(prison.id(), Map.of()).keySet().stream()
                .map(id -> context.platform().targets().state(id))
                .flatMap(Optional::stream)
                .filter(found -> distance(found.location(), self.location()) <= range)
                .filter(found -> minion.mode != Minion.Mode.POST || prison.area().distanceTo(found.location()) <= margin)
                .filter(found -> found.kind() != TargetKind.PLAYER || !prisons.isFriend(prison.id(), found.id()))
                .filter(found -> context.platform().targets().canSee(minion.id, found.id()))
                .min(Comparator.comparingDouble(found -> distance(found.location(), self.location())));
        minions().setAttackTarget(minion.id, target.map(TargetState::id));
        return target.isPresent();
    }

    private void patrol(Minion minion, TargetState self, PrisonInfo prison) {
        Area area = prison.area();
        double wander = context.config().getDouble("minions.patrol_radius", 5);
        if (!area.contains(self.location()) || distance(self.location(), minion.post) > wander + 3) {
            minions().moveTo(minion.id, area.contains(minion.post) ? minion.post : area.center(), 1.0);
            return;
        }
        if (random.getAsDouble() < 0.15) {
            double x = minion.post.x() + (random.getAsDouble() * 2 - 1) * wander;
            double z = minion.post.z() + (random.getAsDouble() * 2 - 1) * wander;
            Location point = new Location(minion.post.worldId(), x, minion.post.y(), z);
            if (area.contains(point)) {
                minions().moveTo(minion.id, point, 0.8);
            }
        }
    }

    private void follow(Minion minion, TargetState self) {
        Optional<TargetState> owner = context.platform().targets().state(minion.owner);
        if (owner.isPresent() && owner.get().location().worldId().equals(self.location().worldId())
                && distance(owner.get().location(), self.location()) > 4) {
            minions().moveTo(minion.id, owner.get().location(), 1.2);
        }
    }

    private void courier(Minion minion, TargetState self, PrisonInfo prison) {
        Job job = jobs.get(minion.id);
        if (job == null) {
            job = idleJob(minion, prison);
            if (job == null) {
                patrol(minion, self, prison);
                return;
            }
            jobs.put(minion.id, job);
        }
        work(minion, self, prison, job);
    }

    /** Поручение без запроса: донести то, что несёт, или перенести сданное на склад. */
    private Job idleJob(Minion minion, PrisonInfo prison) {
        List<BlockLocation> warehouses = prisons.chests(prison.id(), ChestRole.WAREHOUSE);
        if (!minion.carrying.isEmpty()) {
            List<BlockLocation> targets = warehouses.isEmpty() ? prisons.chests(prison.id(), ChestRole.REWARDS) : warehouses;
            return targets.isEmpty() ? null : new Job(Job.Kind.UNLOAD, Optional.empty(), Optional.of(targets.get(0)),
                    Optional.empty(), List.of(), Job.Stage.TO_TARGET, context.clock().millis());
        }
        if (warehouses.isEmpty() || ticks % (THINK_INTERVAL_TICKS * 30) != 0) {
            return null;
        }
        for (BlockLocation deposit : prisons.chests(prison.id(), ChestRole.DEPOSIT)) {
            if (context.platform().containers().contents(deposit).filter(list -> !list.isEmpty()).isPresent()) {
                return new Job(Job.Kind.HAUL, Optional.of(deposit), Optional.of(warehouses.get(0)), Optional.empty(),
                        List.of(), Job.Stage.TO_SOURCE, context.clock().millis());
            }
        }
        return null;
    }

    private void work(Minion minion, TargetState self, PrisonInfo prison, Job job) {
        if (job.stage == Job.Stage.TO_SOURCE) {
            BlockLocation source = job.source.orElseThrow();
            if (!near(self.location(), source)) {
                minions().moveTo(minion.id, center(source), 1.0);
                return;
            }
            List<ItemStackData> taken = new ArrayList<>();
            if (job.kind == Job.Kind.DELIVER) {
                for (ItemStackData wanted : job.wanted) {
                    taken.addAll(context.platform().containers().remove(source, wanted.itemId(), wanted.count()));
                }
            } else {
                taken.addAll(context.platform().containers().takeStacks(source, 9));
            }
            if (taken.isEmpty()) {
                jobs.remove(minion.id);
                if (job.kind == Job.Kind.DELIVER) {
                    notifyArmy(prison.id(), "minions.courier.empty_chest", name(minion.id));
                }
                return;
            }
            minion.carrying.addAll(taken);
            minions().setHeld(minion.id, Optional.of(taken.get(0).withCount(1)));
            save(minion);
            job.stage = Job.Stage.TO_TARGET;
            job.startedAt = context.clock().millis();
            return;
        }
        if (job.captiveTarget.isPresent()) {
            Optional<TargetState> captive = context.platform().targets().state(job.captiveTarget.get());
            if (captive.isEmpty() || !captivity.isCaptive(job.captiveTarget.get())) {
                if (context.clock().millis() - job.startedAt > CAPTIVE_WAIT_MILLIS
                        || !captivity.isCaptive(job.captiveTarget.get())) {
                    jobs.remove(minion.id);
                }
                return;
            }
            if (distance(self.location(), captive.get().location()) > REACH) {
                minions().moveTo(minion.id, captive.get().location(), 1.0);
                return;
            }
            context.platform().inventories().give(job.captiveTarget.get(), List.copyOf(minion.carrying));
            context.platform().feedback().message(job.captiveTarget.get(), text("minions.courier.delivered"));
            finishCarrying(minion);
            return;
        }
        BlockLocation target = job.chestTarget.orElseThrow();
        if (!near(self.location(), target)) {
            minions().moveTo(minion.id, center(target), 1.0);
            return;
        }
        List<ItemStackData> left = context.platform().containers().insert(target, List.copyOf(minion.carrying));
        minion.carrying.clear();
        minion.carrying.addAll(left);
        if (left.isEmpty()) {
            finishCarrying(minion);
        } else if (++job.failures >= 3) {
            jobs.remove(minion.id);
            save(minion);
            notifyArmy(prison.id(), "minions.courier.chest_full", name(minion.id));
        }
    }

    private void finishCarrying(Minion minion) {
        minion.carrying.clear();
        minions().setHeld(minion.id, Optional.empty());
        jobs.remove(minion.id);
        save(minion);
    }

    /** Сломленный фермер складывает урожай в сундук тюрьмы, оставляя себе семена для посадки. */
    private void farm(Minion minion, TargetState self, PrisonInfo prison) {
        if (!minions().profession(minion.id).filter("minecraft:farmer"::equals).isPresent()) {
            return;
        }
        List<BlockLocation> chests = new ArrayList<>(prisons.chests(prison.id(), ChestRole.WAREHOUSE));
        chests.addAll(prisons.chests(prison.id(), ChestRole.DEPOSIT));
        if (chests.isEmpty()) {
            return;
        }
        Map<String, Integer> surplus = surplus(minion.id);
        int total = surplus.values().stream().mapToInt(Integer::intValue).sum();
        if (total < context.config().getInt("minions.farmer_batch", 16)) {
            return;
        }
        BlockLocation chest = chests.stream()
                .min(Comparator.comparingDouble(block -> distance(center(block), self.location()))).orElseThrow();
        if (!near(self.location(), chest)) {
            minions().moveTo(minion.id, center(chest), 0.7);
            return;
        }
        List<ItemStackData> stacks = new ArrayList<>();
        surplus.forEach((id, count) -> {
            int removed = minions().removeFromInventory(minion.id, id, count);
            if (removed > 0) {
                stacks.add(ItemStackData.of(id, removed));
            }
        });
        List<ItemStackData> left = context.platform().containers().insert(chest, stacks);
        if (!left.isEmpty()) {
            minions().drop(minion.id, left);
        }
    }

    /** Урожай сверх того, что фермер оставляет себе: настройка minions.farmer_items, «ID=сколько оставить». */
    private Map<String, Integer> surplus(TargetId villager) {
        Map<String, Integer> keep = new LinkedHashMap<>();
        List<String> rules = context.config().has("minions.farmer_items")
                ? context.config().getStringList("minions.farmer_items")
                : List.of("minecraft:wheat=0", "minecraft:beetroot=0", "minecraft:potato=16", "minecraft:carrot=16");
        for (String rule : rules) {
            int eq = rule.indexOf('=');
            keep.put(eq < 0 ? rule : rule.substring(0, eq), eq < 0 ? 0 : Integer.parseInt(rule.substring(eq + 1).trim()));
        }
        Map<String, Integer> have = new LinkedHashMap<>();
        minions().inventory(villager).forEach(stack -> have.merge(stack.itemId(), stack.count(), Integer::sum));
        Map<String, Integer> surplus = new LinkedHashMap<>();
        keep.forEach((id, kept) -> {
            int extra = have.getOrDefault(id, 0) - kept;
            if (extra > 0) {
                surplus.put(id, extra);
            }
        });
        return surplus;
    }

    // ---------- раздел пульта ----------

    Optional<MenuButton> consoleButton(String prisonId, TargetId viewer) {
        if (!prisons.hasRight(prisonId, viewer, PrisonRight.ARMY)) {
            return Optional.empty();
        }
        long count = minions.values().stream().filter(minion -> minion.prisonId.equals(prisonId)).count();
        return Optional.of(new MenuButton("minecraft:iron_sword", text("minions.menu.button", count),
                List.of(text("minions.menu.button_lore")), () -> openArmy(viewer, prisonId)));
    }

    void openArmy(TargetId viewer, String prisonId) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (Minion minion : minions.values()) {
            if (!minion.prisonId.equals(prisonId) || slot >= 54) {
                continue;
            }
            Optional<TargetState> state = context.platform().targets().state(minion.id);
            List<String> lore = new ArrayList<>();
            lore.add(text("minions.menu.mode", text("minions.mode_name." + minion.mode.name())));
            lore.add(state.map(found -> text("minions.menu.health", Math.round(found.health()), Math.round(found.maxHealth())))
                    .orElse(text("minions.menu.unloaded")));
            state.ifPresent(found -> lore.add(text("minions.menu.where", Math.round(found.location().x()) + " "
                    + Math.round(found.location().y()) + " " + Math.round(found.location().z()))));
            Map<String, String> gear = state.isPresent() ? minions().equipment(minion.id) : Map.of();
            lore.add(gear.isEmpty() ? text("minions.menu.no_gear") : text("minions.menu.gear", String.join(", ",
                    gear.values().stream().map(Army::shortId).toList())));
            if (!minion.carrying.isEmpty()) {
                lore.add(text("minions.menu.carrying", minion.carrying.size()));
            }
            lore.add(text("minions.menu.click"));
            entries.add(new Menus.Entry(slot++, minion.typeId + "_spawn_egg", name(minion.id), lore,
                    () -> openMinion(viewer, minion)));
        }
        if (entries.isEmpty()) {
            entries.add(new Menus.Entry(22, "minecraft:ender_eye", text("minions.menu.empty"),
                    List.of(text("minions.menu.empty_lore")), null));
        }
        context.platform().menus().open(viewer, new Menus.Menu(text("minions.menu.title"), 6, entries));
    }

    private void openMinion(TargetId viewer, Minion minion) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 10;
        for (Minion.Mode mode : Minion.Mode.values()) {
            boolean current = minion.mode == mode;
            entries.add(new Menus.Entry(slot, current ? "minecraft:lime_dye" : "minecraft:gray_dye",
                    text("minions.mode_name." + mode.name()), List.of(text("minions.mode_lore." + mode.name())), () -> {
                        minion.mode = mode;
                        if (mode == Minion.Mode.FOLLOW) {
                            minion.owner = viewer;
                        }
                        context.platform().targets().state(minion.id).ifPresent(state -> {
                            if (mode == Minion.Mode.POST) {
                                minion.post = state.location();
                            }
                        });
                        jobs.remove(minion.id);
                        save(minion);
                        openMinion(viewer, minion);
                    }));
            slot += 2;
        }
        entries.add(new Menus.Entry(16, "minecraft:barrier", text("minions.menu.dismiss"),
                List.of(text("minions.menu.dismiss_lore")), () -> {
                    minions().free(minion.id);
                    minions().setLabel(minion.id, "");
                    remove(minion);
                    openArmy(viewer, minion.prisonId);
                }));
        entries.add(new Menus.Entry(22, "minecraft:arrow", text("prison.menu.back"), List.of(),
                () -> openArmy(viewer, minion.prisonId)));
        context.platform().menus().open(viewer, new Menus.Menu(name(minion.id), 3, entries));
    }

    // ---------- общее ----------

    private String label(Minion minion, PrisonInfo prison) {
        return text("minions.label." + (minion.isVillager() ? "VILLAGER" : minion.mode.name()), prison.name());
    }

    private void notifyArmy(String prisonId, String key, Object... args) {
        for (TargetState player : context.platform().players().online()) {
            if (prisons.hasRight(prisonId, player.id(), PrisonRight.ARMY)) {
                context.platform().feedback().message(player.id(), text(key, args));
            }
        }
    }

    private List<String> golemTypes() {
        return context.config().has("minions.golem_types") ? context.config().getStringList("minions.golem_types")
                : List.of("minecraft:iron_golem", "minecraft:snow_golem");
    }

    private String controlItem() {
        return context.config().getString("minions.control_item", "minecraft:ender_eye");
    }

    private Minions minions() {
        return context.platform().minions();
    }

    private String name(TargetId id) {
        return context.platform().targets().state(id).map(TargetState::typeId).map(Army::shortId)
                .orElse(id.value().toString().substring(0, 8));
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private void bar(TargetId player, String text) {
        context.platform().feedback().actionBar(player, text);
    }

    private String text(String key, Object... args) {
        return context.messages().text(key, args);
    }

    private static boolean near(Location location, BlockLocation block) {
        return distance(location, center(block)) <= REACH;
    }

    private static Location center(BlockLocation block) {
        return new Location(block.worldId(), block.x() + 0.5, block.y(), block.z() + 0.5);
    }

    private static double distance(Location a, Location b) {
        if (!a.worldId().equals(b.worldId())) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double num(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
