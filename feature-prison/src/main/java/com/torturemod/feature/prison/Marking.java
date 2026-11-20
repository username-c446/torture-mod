package com.torturemod.feature.prison;

import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.Area;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.PrisonRight;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Разметка без команд. Предмет разметки: ЛКМ по блоку — первый угол, ПКМ по блоку — второй,
 * Shift+ПКМ в воздух — подтвердить, Shift+ЛКМ — сбросить. Shift+ПКМ по сундуку или кафедре в тюрьме
 * меняет их назначение. Границы видны частицами только тому, кто размечает.
 */
final class Marking {
    private static final int SELECTION_PARTICLES_TICKS = 10;
    private static final int PRISON_PARTICLES_TICKS = 20;
    private static final double VIEW_DISTANCE = 64;
    private static final List<ChestRole> CHEST_CYCLE = List.of(ChestRole.DEPOSIT, ChestRole.REWARDS,
            ChestRole.RANSOM, ChestRole.WAREHOUSE);

    private final Support s;
    private final Map<TargetId, BlockLocation[]> selections = new HashMap<>();
    /** Кто в этом тике уже нажал ПКМ по блоку: следом игра присылает то же нажатие как ПКМ в воздух. */
    private final Set<TargetId> usedBlockThisTick = new HashSet<>();
    private long ticks;

    Marking(Support support) {
        this.s = support;
    }

    private boolean isMarker(Optional<String> item) {
        return item.filter(s.settings().markerItem()::equals).isPresent();
    }

    void onAttack(GameEvent.BlockAttacked event) {
        if (event.cancellation().isCancelled() || !isMarker(event.itemId()) || s.captivity.isCaptive(event.actor())) {
            return;
        }
        event.cancellation().cancel();
        if (event.sneaking()) {
            reset(event.actor());
            return;
        }
        BlockLocation[] corners = selections.computeIfAbsent(event.actor(), key -> new BlockLocation[2]);
        corners[0] = event.block();
        if (corners[1] != null && !corners[1].worldId().equals(event.block().worldId())) {
            corners[1] = null;
        }
        report(event.actor(), corners, "prison.mark.first");
        s.hint(event.actor(), "hint.prison.marking");
    }

    void onSwing(GameEvent.ArmSwung event) {
        if (isMarker(event.itemId()) && event.sneaking() && !s.captivity.isCaptive(event.actor())) {
            reset(event.actor());
        }
    }

    void onBlockUse(GameEvent.BlockInteracted event) {
        if (event.cancellation().isCancelled() || !isMarker(event.itemId()) || s.captivity.isCaptive(event.actor())) {
            return;
        }
        usedBlockThisTick.add(event.actor());
        event.cancellation().cancel();
        if (event.sneaking()) {
            designate(event.actor(), event.block());
            return;
        }
        BlockLocation[] corners = selections.computeIfAbsent(event.actor(), key -> new BlockLocation[2]);
        corners[1] = event.block();
        if (corners[0] != null && !corners[0].worldId().equals(event.block().worldId())) {
            corners[0] = null;
        }
        report(event.actor(), corners, "prison.mark.second");
    }

    void onItemUse(GameEvent.ItemUsed event) {
        if (event.cancellation().isCancelled() || !event.sneaking()
                || !s.settings().markerItem().equals(event.itemId()) || s.captivity.isCaptive(event.actor())
                || usedBlockThisTick.contains(event.actor())) {
            return;
        }
        event.cancellation().cancel();
        confirm(event.actor());
    }

    void forget(TargetId player) {
        selections.remove(player);
    }

    void tick() {
        ticks++;
        usedBlockThisTick.clear();
        if (ticks % SELECTION_PARTICLES_TICKS == 0) {
            selections.forEach((player, corners) -> {
                if (corners[0] != null && corners[1] != null) {
                    outline(player, Area.between(corners[0], corners[1]), "minecraft:flame");
                } else if (corners[0] != null || corners[1] != null) {
                    BlockLocation one = corners[0] != null ? corners[0] : corners[1];
                    outline(player, Area.between(one, one), "minecraft:flame");
                }
            });
        }
        if (ticks % PRISON_PARTICLES_TICKS == 0) {
            for (TargetState player : s.context.platform().players().online()) {
                if (!isMarker(s.context.platform().items().heldItem(player.id()))) {
                    continue;
                }
                for (Prison prison : s.registry.prisons.values()) {
                    if (prison.area.distanceTo(player.location()) > VIEW_DISTANCE) {
                        continue;
                    }
                    outline(player.id(), prison.area, "minecraft:happy_villager");
                    if (s.registry.isTeam(prison, player.id())) {
                        prison.zones.forEach(zone -> outline(player.id(), zone.area, "minecraft:soul_fire_flame"));
                    }
                }
            }
        }
    }

    private void reset(TargetId player) {
        selections.remove(player);
        s.message(player, "prison.mark.reset");
    }

    private void report(TargetId player, BlockLocation[] corners, String key) {
        BlockLocation corner = key.endsWith("first") ? corners[0] : corners[1];
        s.message(player, key, corner.x() + " " + corner.y() + " " + corner.z());
        if (corners[0] != null && corners[1] != null) {
            Area area = Area.between(corners[0], corners[1]);
            s.message(player, "prison.mark.size", area.sizeX(), area.sizeY(), area.sizeZ(), area.volume());
            Optional<Prison> inside = s.registry.prisons.values().stream()
                    .filter(prison -> prison.area.contains(area)).findFirst();
            s.message(player, inside.isPresent() ? "prison.mark.confirm_zone" : "prison.mark.confirm_prison",
                    inside.map(prison -> prison.name).orElse(""));
        } else {
            s.message(player, corners[0] == null ? "prison.mark.need_first" : "prison.mark.need_second");
        }
    }

    /** Подтверждение: внутри своей тюрьмы — зона, вне тюрем — новая тюрьма. */
    private void confirm(TargetId player) {
        BlockLocation[] corners = selections.get(player);
        if (corners == null || corners[0] == null || corners[1] == null) {
            s.message(player, "prison.mark.incomplete");
            return;
        }
        Area area = Area.between(corners[0], corners[1]);
        Optional<Prison> container = s.registry.prisons.values().stream()
                .filter(prison -> prison.area.contains(area)).findFirst();
        if (container.isPresent()) {
            createZone(player, container.get(), area);
            return;
        }
        Optional<Prison> overlap = s.registry.prisons.values().stream()
                .filter(prison -> prison.area.intersects(area)).findFirst();
        if (overlap.isPresent()) {
            s.message(player, "prison.mark.overlap", overlap.get().name);
            return;
        }
        createPrison(player, area);
    }

    private void createPrison(TargetId player, Area area) {
        PrisonSettings settings = s.settings();
        if (!s.context.platform().permissions().has(player, Permissions.USE) || !s.registry.mayRegister(player, area)) {
            s.message(player, "prison.create.denied");
            return;
        }
        int min = Math.min(area.sizeX(), Math.min(area.sizeY(), area.sizeZ()));
        int max = Math.max(area.sizeX(), Math.max(area.sizeY(), area.sizeZ()));
        if (min < settings.minSide() || max > settings.maxSide()) {
            s.message(player, "prison.create.size", settings.minSide(), settings.maxSide());
            return;
        }
        if (s.registry.ownedBy(player) >= settings.maxPrisonsPerPlayer()
                && !s.context.platform().permissions().has(player, Permissions.ADMIN)) {
            s.message(player, "prison.create.limit", settings.maxPrisonsPerPlayer());
            return;
        }
        long number = s.registry.ownedBy(player) + 1;
        String name = s.text("prison.default_name", s.name(player)) + (number > 1 ? " " + number : "");
        Prison prison = s.registry.create(name, player, area);
        selections.remove(player);
        s.message(player, "prison.created", prison.name, area.sizeX(), area.sizeY(), area.sizeZ());
        s.hint(player, "hint.prison.created");
    }

    private void createZone(TargetId player, Prison prison, Area area) {
        if (!s.registry.has(prison, player, PrisonRight.MANAGE)) {
            s.message(player, "prison.no_right");
            return;
        }
        if (prison.zones.size() >= s.settings().maxZones()) {
            s.message(player, "prison.zone.limit", s.settings().maxZones());
            return;
        }
        String name = s.text("prison.zone.default_name", prison.zones.size() + 1);
        prison.zones.add(new Zone(name, area, prison.zones.size(), EnumSet.allOf(Flag.class)));
        s.registry.save(prison);
        selections.remove(player);
        s.message(player, "prison.zone.created", name, prison.name);
        s.hint(player, "hint.prison.zone");
    }

    /** Shift+ПКМ предметом разметки по сундуку или кафедре внутри тюрьмы. */
    private void designate(TargetId player, BlockLocation block) {
        Optional<Prison> prison = s.registry.at(block);
        Optional<String> blockId = s.context.platform().blocks().blockId(block);
        if (prison.isEmpty() || blockId.isEmpty()) {
            s.message(player, "prison.designate.outside");
            return;
        }
        if (!s.registry.has(prison.get(), player, PrisonRight.MANAGE)) {
            s.message(player, "prison.no_right");
            return;
        }
        if (s.settings().consoleBlocks().contains(blockId.get())) {
            if (prison.get().consoles.remove(block)) {
                s.message(player, "prison.console.removed");
            } else {
                prison.get().consoles.add(block);
                s.message(player, "prison.console.added", prison.get().name);
                s.hint(player, "hint.prison.console");
            }
            s.registry.save(prison.get());
            return;
        }
        if (!s.settings().chestBlocks().contains(blockId.get())) {
            s.message(player, "prison.designate.unknown");
            return;
        }
        ChestRole current = prison.get().chests.get(block);
        int next = current == null ? 0 : CHEST_CYCLE.indexOf(current) + 1;
        if (next >= CHEST_CYCLE.size()) {
            prison.get().chests.remove(block);
            s.message(player, "prison.chest.cleared");
        } else {
            prison.get().chests.put(block, CHEST_CYCLE.get(next));
            s.message(player, "prison.chest." + CHEST_CYCLE.get(next).name());
        }
        s.registry.save(prison.get());
    }

    /** Рёбра параллелепипеда частицами, не больше ~25 точек на ребро. */
    private void outline(TargetId viewer, Area area, String particle) {
        Optional<TargetState> state = s.context.platform().targets().state(viewer);
        if (state.isEmpty() || area.distanceTo(state.get().location()) > VIEW_DISTANCE) {
            return;
        }
        double x1 = area.minX(), y1 = area.minY(), z1 = area.minZ();
        double x2 = area.maxX() + 1, y2 = area.maxY() + 1, z2 = area.maxZ() + 1;
        double[][] corners = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
                {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            double length = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
            int steps = (int) Math.max(1, Math.min(25, length));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                s.context.platform().feedback().particleFor(viewer, new Location(area.worldId(),
                        a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t), particle);
            }
        }
    }
}
