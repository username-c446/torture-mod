package com.torturemod.testkit;

import com.torturemod.api.port.ResourcePacks;
import com.torturemod.api.items.ShapedRecipe;
import com.torturemod.api.items.CustomItem;
import com.torturemod.api.port.Minions;
import com.torturemod.api.port.Containers;
import com.torturemod.api.model.Book;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.Platform;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.DamageKind;
import com.torturemod.api.model.InventoryKind;
import com.torturemod.api.model.ItemEntry;
import com.torturemod.api.model.ItemTraits;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.BlockQueries;
import com.torturemod.api.port.Bodies;
import com.torturemod.api.port.Combat;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Convoy;
import com.torturemod.api.port.Feedback;
import com.torturemod.api.port.GameEvents;
import com.torturemod.api.port.Inventories;
import com.torturemod.api.port.Items;
import com.torturemod.api.port.Log;
import com.torturemod.api.port.Menus;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.port.Players;
import com.torturemod.api.port.RegionPolicy;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.port.Targets;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Мир в памяти для тестов фич: блоки, цели, сообщения и тики задаются вручную. */
public final class FakePlatform implements Platform {
    public static final String AIR = "minecraft:air";

    public final Map<BlockLocation, String> blocks = new HashMap<>();
    public final Set<String> unloadedWorlds = new HashSet<>();
    public final Map<TargetId, TargetState> targets = new LinkedHashMap<>();
    public final Set<TargetId> persistent = new HashSet<>();
    public final Map<TargetId, List<String>> messages = new HashMap<>();
    public final Map<TargetId, List<String>> actionBars = new HashMap<>();
    public final List<String> consumed = new ArrayList<>();
    public final Set<String> grants = new HashSet<>();
    public final Map<String, ItemTraits> itemTraits = new LinkedHashMap<>();
    public final List<String> errors = new ArrayList<>();
    public RegionPolicy regionPolicy = RegionPolicy.ALLOW_ALL;
    /** Инвентари игроков: слот → предмет. Слот 0 считается основной рукой. */
    public final Map<TargetId, Map<Integer, String>> inventories = new HashMap<>();
    public final List<String> spent = new ArrayList<>();
    public final List<String> combatLog = new ArrayList<>();
    public final Set<TargetId> fireImmune = new HashSet<>();
    public final Set<TargetId> waterBreathing = new HashSet<>();
    public final Set<TargetId> poisonImmune = new HashSet<>();
    public final Map<TargetId, Menus.Menu> openMenus = new HashMap<>();
    public final Map<String, Float> bars = new HashMap<>();
    /** Игроки с телом в мире; restored и removedBodies записывают вызовы порта. */
    public final Set<TargetId> bodies = new HashSet<>();
    public final List<TargetId> restoredBodies = new ArrayList<>();
    public final List<TargetId> removedBodies = new ArrayList<>();
    public final Map<TargetId, BlockLocation> respawnPoints = new HashMap<>();
    public final Map<TargetId, BlockLocation> deaths = new HashMap<>();
    public final Map<TargetId, String> teams = new HashMap<>();
    /** Предметы эндер-сундука: слот → предмет. */
    public final Map<TargetId, Map<Integer, String>> enderChests = new HashMap<>();
    /** Пленный → палач с видимой верёвкой. */
    public final Map<TargetId, TargetId> ropes = new HashMap<>();
    public final List<String> pushes = new ArrayList<>();
    /** Транспорт: по одному месту в каждом. */
    public final Set<TargetId> vehicles = new HashSet<>();
    public final Map<TargetId, TargetId> riding = new HashMap<>();

    /** Хранилища: блок → стопки; вместимость 27 стопок. */
    public final Map<BlockLocation, List<ItemStackData>> chests = new HashMap<>();
    public final Set<String> food = new HashSet<>();
    public final List<String> maps = new ArrayList<>();
    public final Map<TargetId, List<ItemStackData>> given = new HashMap<>();
    public final Map<TargetId, Book> books = new HashMap<>();
    public int viewerParticles;
    /** Пары «кто|кого», которые не видят друг друга; остальные видят, если в одном мире. */
    public final Set<String> hiddenPairs = new HashSet<>();
    public final Map<TargetId, Menus.StorageMenu> openStorage = new HashMap<>();
    // Слуги.
    public final Set<TargetId> enslaved = new HashSet<>();
    public final Map<TargetId, Location> moves = new HashMap<>();
    public final Map<TargetId, TargetId> attackTargets = new HashMap<>();
    public final Map<TargetId, String> labels = new HashMap<>();
    public final Map<TargetId, ItemStackData> held = new HashMap<>();
    public final Map<TargetId, Map<String, String>> equipment = new HashMap<>();
    public final Map<TargetId, List<ItemStackData>> mobInventories = new HashMap<>();
    public final List<String> befriended = new ArrayList<>();
    public final Map<TargetId, String> professions = new HashMap<>();
    public final List<ItemStackData> dropped = new ArrayList<>();
    public final Map<String, CustomItem> customItems = new HashMap<>();
    public final List<ShapedRecipe> recipes = new ArrayList<>();
    /** Предложения ресурспака: «ссылка|sha1». */
    public final List<String> packOffers = new ArrayList<>();
    public Path configDirectory = Path.of("config");
    private final List<Consumer<GameEvent>> listeners = new ArrayList<>();
    private final FakeScheduler scheduler = new FakeScheduler();

    // ---------- настройка мира ----------

    public void setBlock(String world, int x, int y, int z, String id) {
        blocks.put(new BlockLocation(world, x, y, z), id);
    }

    /** Полая коробка: стены, пол и потолок из одного блока, внутри воздух. */
    public void hollowBox(String world, int x1, int y1, int z1, int x2, int y2, int z2, String wall) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                    setBlock(world, x, y, z, edge ? wall : AIR);
                }
            }
        }
    }

    public TargetId player(String name, Location location) {
        TargetId id = new TargetId(UUID.nameUUIDFromBytes(name.getBytes()));
        targets.put(id, new TargetState(id, TargetKind.PLAYER, "minecraft:player", name, location, 20, 20));
        return id;
    }

    public TargetId mob(String typeId, Location location) {
        TargetId id = new TargetId(UUID.randomUUID());
        targets.put(id, new TargetState(id, TargetKind.MOB, typeId, typeId, location, 20, 20));
        return id;
    }

    public void move(TargetId id, Location location) {
        TargetState state = targets.get(id);
        targets.put(id, new TargetState(id, state.kind(), state.typeId(), state.name(), location,
                state.health(), state.maxHealth()));
    }

    public void grant(TargetId player, String node) {
        grants.add(player.value() + "|" + node);
    }

    public void fire(GameEvent event) {
        listeners.forEach(listener -> listener.accept(event));
    }

    public void tick(int count) {
        for (int i = 0; i < count; i++) {
            scheduler.tick();
        }
    }

    public void give(TargetId player, int slot, String itemId) {
        inventories.computeIfAbsent(player, key -> new HashMap<>()).put(slot, itemId);
    }

    /** Нажимает на слот открытого меню. */
    public void click(TargetId viewer, int slot) {
        Menus.Menu menu = openMenus.get(viewer);
        if (menu == null) {
            throw new IllegalStateException("No menu is open");
        }
        menu.entries().stream().filter(entry -> entry.slot() == slot).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No entry in slot " + slot))
                .onClick().run();
    }

    /** Закрывает меню-хранилище с этим содержимым. */
    public void closeStorage(TargetId viewer, List<ItemStackData> contents) {
        Menus.StorageMenu menu = openStorage.remove(viewer);
        if (menu == null) {
            throw new IllegalStateException("No storage menu is open");
        }
        menu.onClose().accept(List.copyOf(contents));
    }

    public List<ItemStackData> givenTo(TargetId player) {
        return given.getOrDefault(player, List.of());
    }

    public int countIn(BlockLocation chest, String itemId) {
        return chests.getOrDefault(chest, List.of()).stream().filter(s -> s.itemId().equals(itemId))
                .mapToInt(ItemStackData::count).sum();
    }

    public float health(TargetId id) {
        return targets.get(id).health();
    }

    public List<String> messagesOf(TargetId id) {
        return messages.getOrDefault(id, List.of());
    }

    public List<String> actionBarsOf(TargetId id) {
        return actionBars.getOrDefault(id, List.of());
    }

    // ---------- порты ----------

    @Override
    public Log log() {
        return new Log() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void error(String message, Throwable cause) {
                errors.add(message);
            }
        };
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public Targets targets() {
        return new Targets() {
            @Override
            public Optional<TargetState> state(TargetId id) {
                return Optional.ofNullable(targets.get(id));
            }

            @Override
            public List<TargetState> livingIn(String worldId, BlockLocation min, BlockLocation max) {
                return targets.values().stream().filter(target -> {
                    BlockLocation block = BlockLocation.of(target.location());
                    return block.worldId().equals(worldId)
                            && block.x() >= min.x() && block.x() <= max.x()
                            && block.y() >= min.y() && block.y() <= max.y()
                            && block.z() >= min.z() && block.z() <= max.z();
                }).toList();
            }

            @Override
            public List<TargetState> playersNear(Location center, double radius) {
                return targets.values().stream()
                        .filter(target -> target.kind() == TargetKind.PLAYER)
                        .filter(target -> target.location().worldId().equals(center.worldId()))
                        .filter(target -> distanceSquared(target.location(), center) <= radius * radius)
                        .toList();
            }

            @Override
            public Optional<TargetId> resolvePlayer(String nameOrUuid) {
                return targets.values().stream()
                        .filter(target -> target.kind() == TargetKind.PLAYER)
                        .filter(target -> target.name().equals(nameOrUuid)
                                || target.id().value().toString().equals(nameOrUuid))
                        .map(TargetState::id)
                        .findFirst();
            }

            @Override
            public Optional<String> playerName(TargetId player) {
                return Optional.ofNullable(targets.get(player)).map(TargetState::name);
            }

            @Override
            public void setPersistent(TargetId id) {
                persistent.add(id);
            }

            @Override
            public boolean canSee(TargetId observer, TargetId target) {
                TargetState a = targets.get(observer);
                TargetState b = targets.get(target);
                return a != null && b != null && a.location().worldId().equals(b.location().worldId())
                        && !hiddenPairs.contains(observer.value() + "|" + target.value());
            }
        };
    }

    @Override
    public Players players() {
        return new Players() {
            @Override
            public List<TargetState> online() {
                return targets.values().stream().filter(target -> target.kind() == TargetKind.PLAYER).toList();
            }

            @Override
            public Optional<BlockLocation> respawnPoint(TargetId player) {
                return Optional.ofNullable(respawnPoints.get(player));
            }

            @Override
            public Optional<BlockLocation> lastDeath(TargetId player) {
                return Optional.ofNullable(deaths.get(player));
            }

            @Override
            public Optional<String> team(TargetId player) {
                return Optional.ofNullable(teams.get(player));
            }

            @Override
            public List<String> teamMembers(String team) {
                return teams.entrySet().stream().filter(entry -> entry.getValue().equals(team))
                        .map(entry -> targets.get(entry.getKey()).name()).sorted().toList();
            }
        };
    }

    @Override
    public BlockQueries blocks() {
        return location -> unloadedWorlds.contains(location.worldId())
                ? Optional.empty()
                : Optional.of(blocks.getOrDefault(location, AIR));
    }

    @Override
    public Items items() {
        return new Items() {
            @Override
            public Optional<ItemTraits> traits(String itemId) {
                return Optional.ofNullable(itemTraits.get(itemId));
            }

            @Override
            public Iterable<String> allItemIds() {
                return itemTraits.keySet();
            }

            @Override
            public Optional<String> heldItem(TargetId actor) {
                return Optional.ofNullable(inventories.getOrDefault(actor, Map.of()).get(0));
            }

            @Override
            public OptionalInt findInHands(TargetId actor, String itemId) {
                Map<Integer, String> inventory = inventories.getOrDefault(actor, Map.of());
                if (itemId.equals(inventory.get(0))) {
                    return OptionalInt.of(0);
                }
                return itemId.equals(inventory.get(OFFHAND_SLOT)) ? OptionalInt.of(OFFHAND_SLOT) : OptionalInt.empty();
            }

            @Override
            public void consume(TargetId actor, String itemId, int count) {
                consumed.add(actor.value() + "|" + itemId + "|" + count);
            }

            @Override
            public void spend(TargetId actor, int slot) {
                spent.add(actor.value() + "|" + slot);
            }

            @Override
            public void defineCustomItem(CustomItem item) {
                customItems.put(item.id(), item);
            }

            @Override
            public void addRecipe(ShapedRecipe recipe) {
                recipes.add(recipe);
            }

            @Override
            public boolean isFood(String itemId) {
                return food.contains(itemId);
            }

            @Override
            public void giveMap(TargetId player, String name, int[] argb128) {
                maps.add(player.value() + "|" + name + "|" + argb128.length);
            }
        };
    }

    @Override
    public Feedback feedback() {
        return new Feedback() {
            @Override
            public void message(TargetId receiver, String text) {
                messages.computeIfAbsent(receiver, key -> new ArrayList<>()).add(text);
            }

            @Override
            public void actionBar(TargetId receiver, String text) {
                actionBars.computeIfAbsent(receiver, key -> new ArrayList<>()).add(text);
            }

            @Override
            public void sound(Location location, String soundId) {
            }

            @Override
            public void particles(Location location, String particleId, int count) {
            }

            @Override
            public void particleFor(TargetId viewer, Location location, String particleId) {
                viewerParticles++;
            }

            @Override
            public void openBook(TargetId viewer, Book book) {
                books.put(viewer, book);
            }

            @Override
            public void showBar(TargetId viewer, String key, String title, float progress) {
                bars.put(viewer.value() + "|" + key, progress);
            }

            @Override
            public void hideBar(TargetId viewer, String key) {
                bars.remove(viewer.value() + "|" + key);
            }
        };
    }

    @Override
    public Permissions permissions() {
        return (player, node) -> Permissions.USE.equals(node) || grants.contains(player.value() + "|" + node);
    }

    @Override
    public RegionPolicy regionPolicy() {
        return (actor, location, action) -> regionPolicy.allows(actor, location, action);
    }

    @Override
    public Commands commands() {
        return (root, handler) -> {
        };
    }

    @Override
    public GameEvents gameEvents() {
        return listeners::add;
    }

    @Override
    public Combat combat() {
        return new Combat() {
            @Override
            public boolean damage(TargetId target, DamageKind kind, float amount, Optional<TargetId> source) {
                combatLog.add("damage " + kind + " " + amount);
                hurt(target, amount);
                return true;
            }

            @Override
            public boolean ignite(TargetId target, int ticks) {
                combatLog.add("ignite " + ticks);
                return !fireImmune.contains(target);
            }

            @Override
            public boolean drainAir(TargetId target, int ticks) {
                combatLog.add("air " + ticks);
                return !waterBreathing.contains(target);
            }

            @Override
            public boolean drainFood(TargetId target, int points) {
                combatLog.add("food " + points);
                return targets.get(target).kind() == TargetKind.PLAYER;
            }

            @Override
            public boolean applyEffect(TargetId target, String effectId, int ticks, int amplifier) {
                combatLog.add("effect " + effectId + " " + ticks);
                return !(effectId.equals("minecraft:poison") && poisonImmune.contains(target));
            }

            @Override
            public void kill(TargetId target) {
                combatLog.add("kill");
                fire(new GameEvent.Died(target));
            }
        };
    }

    @Override
    public Menus menus() {
        return new Menus() {
            @Override
            public void open(TargetId viewer, Menu menu) {
                openMenus.put(viewer, menu);
            }

            @Override
            public void close(TargetId viewer) {
                openMenus.remove(viewer);
            }

            @Override
            public void openStorage(TargetId viewer, StorageMenu menu) {
                openStorage.put(viewer, menu);
            }
        };
    }

    @Override
    public Inventories inventories() {
        return new Inventories() {
            @Override
            public List<ItemEntry> contents(TargetId owner, InventoryKind kind) {
                Map<TargetId, Map<Integer, String>> source = kind == InventoryKind.MAIN ? inventories : enderChests;
                return source.getOrDefault(owner, Map.of()).entrySet().stream()
                        .map(entry -> new ItemEntry(entry.getKey(), entry.getValue(), 1))
                        .toList();
            }

            @Override
            public boolean take(TargetId taker, TargetId owner, InventoryKind kind, int slot) {
                return false;
            }

            @Override
            public void give(TargetId player, List<ItemStackData> stacks) {
                given.computeIfAbsent(player, key -> new ArrayList<>()).addAll(stacks);
            }
        };
    }

    @Override
    public Bodies bodies() {
        return new Bodies() {
            @Override
            public boolean spawn(TargetId player) {
                return bodies.add(player);
            }

            @Override
            public boolean exists(TargetId player) {
                return bodies.contains(player);
            }

            @Override
            public void restore(TargetId player) {
                if (bodies.remove(player)) {
                    restoredBodies.add(player);
                }
            }

            @Override
            public void remove(TargetId player) {
                if (bodies.remove(player)) {
                    removedBodies.add(player);
                }
            }
        };
    }

    @Override
    public Convoy convoy() {
        return new Convoy() {
            @Override
            public void showLeash(TargetId captive, TargetId holder) {
                ropes.put(captive, holder);
            }

            @Override
            public void hideLeash(TargetId captive) {
                ropes.remove(captive);
            }

            @Override
            public void push(TargetId target, double vx, double vy, double vz) {
                pushes.add(target.value() + "|" + Math.signum(vx) + "|" + Math.signum(vz));
            }

            @Override
            public boolean isVehicle(TargetId entity) {
                return vehicles.contains(entity);
            }

            @Override
            public boolean mount(TargetId rider, TargetId vehicle) {
                if (riding.containsValue(vehicle)) {
                    return false;
                }
                riding.put(rider, vehicle);
                return true;
            }

            @Override
            public void dismount(TargetId rider) {
                TargetId vehicle = riding.get(rider);
                if (vehicle == null) {
                    return;
                }
                Cancellation cancellation = new Cancellation();
                fire(new GameEvent.DismountAttempted(rider, vehicle, cancellation));
                if (!cancellation.isCancelled()) {
                    riding.remove(rider);
                }
            }

            @Override
            public Optional<TargetId> vehicleOf(TargetId rider) {
                return Optional.ofNullable(riding.get(rider));
            }
        };
    }

    @Override
    public Containers containers() {
        return new Containers() {
            @Override
            public Optional<List<ItemStackData>> contents(BlockLocation block) {
                return Optional.ofNullable(chests.get(block)).map(List::copyOf);
            }

            @Override
            public List<ItemStackData> insert(BlockLocation block, List<ItemStackData> stacks) {
                List<ItemStackData> chest = chests.get(block);
                if (chest == null) {
                    return List.copyOf(stacks);
                }
                List<ItemStackData> left = new ArrayList<>();
                for (ItemStackData stack : stacks) {
                    if (chest.size() < 27) {
                        chest.add(stack);
                    } else {
                        left.add(stack);
                    }
                }
                return left;
            }

            @Override
            public List<ItemStackData> remove(BlockLocation block, String itemId, int count) {
                List<ItemStackData> chest = chests.get(block);
                List<ItemStackData> taken = new ArrayList<>();
                if (chest == null) {
                    return taken;
                }
                int need = count;
                for (int i = 0; i < chest.size() && need > 0; i++) {
                    ItemStackData stack = chest.get(i);
                    if (!stack.itemId().equals(itemId)) {
                        continue;
                    }
                    int take = Math.min(need, stack.count());
                    taken.add(stack.withCount(take));
                    need -= take;
                    if (take == stack.count()) {
                        chest.remove(i--);
                    } else {
                        chest.set(i, stack.withCount(stack.count() - take));
                    }
                }
                return taken;
            }

            @Override
            public List<ItemStackData> takeStacks(BlockLocation block, int maxStacks) {
                List<ItemStackData> chest = chests.get(block);
                List<ItemStackData> taken = new ArrayList<>();
                while (chest != null && !chest.isEmpty() && taken.size() < maxStacks) {
                    taken.add(chest.remove(0));
                }
                return taken;
            }
        };
    }

    @Override
    public Minions minions() {
        return new Minions() {
            @Override
            public boolean enslave(TargetId mob) {
                if (!targets.containsKey(mob)) {
                    return false;
                }
                enslaved.add(mob);
                return true;
            }

            @Override
            public void free(TargetId mob) {
                enslaved.remove(mob);
            }

            @Override
            public void moveTo(TargetId mob, Location to, double speed) {
                moves.put(mob, to);
            }

            @Override
            public void setAttackTarget(TargetId mob, Optional<TargetId> target) {
                if (target.isPresent()) {
                    attackTargets.put(mob, target.get());
                } else {
                    attackTargets.remove(mob);
                }
            }

            @Override
            public void setLabel(TargetId mob, String label) {
                labels.put(mob, label);
            }

            @Override
            public void setHeld(TargetId mob, Optional<ItemStackData> stack) {
                if (stack.isPresent()) {
                    held.put(mob, stack.get());
                } else {
                    held.remove(mob);
                }
            }

            @Override
            public boolean equipFromHand(TargetId player, TargetId mob) {
                String item = inventories.getOrDefault(player, Map.of()).get(0);
                if (item == null) {
                    return false;
                }
                String slot = item.endsWith("_helmet") ? "head" : item.endsWith("_chestplate") ? "chest" : "mainhand";
                String old = equipment.computeIfAbsent(mob, key -> new HashMap<>()).put(slot, item);
                if (old == null) {
                    inventories.get(player).remove(0);
                } else {
                    inventories.get(player).put(0, old);
                }
                return true;
            }

            @Override
            public Map<String, String> equipment(TargetId mob) {
                return Map.copyOf(equipment.getOrDefault(mob, Map.of()));
            }

            @Override
            public List<ItemStackData> inventory(TargetId mob) {
                return List.copyOf(mobInventories.getOrDefault(mob, List.of()));
            }

            @Override
            public int removeFromInventory(TargetId mob, String itemId, int count) {
                List<ItemStackData> inventory = mobInventories.getOrDefault(mob, new ArrayList<>());
                int removed = 0;
                for (int i = 0; i < inventory.size() && removed < count; i++) {
                    ItemStackData stack = inventory.get(i);
                    if (!stack.itemId().equals(itemId)) {
                        continue;
                    }
                    int take = Math.min(count - removed, stack.count());
                    removed += take;
                    if (take == stack.count()) {
                        inventory.remove(i--);
                    } else {
                        inventory.set(i, stack.withCount(stack.count() - take));
                    }
                }
                return removed;
            }

            @Override
            public void befriend(TargetId villager, TargetId player) {
                befriended.add(villager.value() + "|" + player.value());
            }

            @Override
            public void drop(TargetId mob, List<ItemStackData> stacks) {
                dropped.addAll(stacks);
            }

            @Override
            public Optional<String> profession(TargetId villager) {
                return Optional.ofNullable(professions.get(villager));
            }
        };
    }

    @Override
    public ResourcePacks resourcePacks() {
        return (player, url, sha1, required, prompt) -> packOffers.add(url + "|" + sha1);
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public Path dataDirectory() {
        return Path.of("data");
    }

    private void hurt(TargetId id, float amount) {
        TargetState state = targets.get(id);
        targets.put(id, new TargetState(id, state.kind(), state.typeId(), state.name(), state.location(),
                Math.max(0, state.health() - amount), state.maxHealth()));
    }

    private static double distanceSquared(Location a, Location b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class FakeScheduler implements Scheduler {
        private final List<Entry> entries = new ArrayList<>();

        void tick() {
            for (Entry entry : List.copyOf(entries)) {
                if (entry.cancelled) {
                    entries.remove(entry);
                } else if (entry.delay > 0) {
                    entry.delay--;
                } else {
                    entry.action.run();
                    if (!entry.repeating) {
                        entries.remove(entry);
                    }
                }
            }
        }

        @Override
        public Task everyTick(Runnable action) {
            Entry entry = new Entry(action, 0, true);
            entries.add(entry);
            return entry;
        }

        @Override
        public Task later(int ticks, Runnable action) {
            Entry entry = new Entry(action, ticks, false);
            entries.add(entry);
            return entry;
        }

        @Override
        public void runOnMainThread(Runnable action) {
            action.run();
        }

        /** В тестах фон выполняется сразу: так результат детерминирован. */
        @Override
        public void runAsync(Runnable work) {
            work.run();
        }

        @Override
        public TickStats takeStats() {
            return new TickStats(0, 0, 0);
        }

        private static final class Entry implements Task {
            private final Runnable action;
            private final boolean repeating;
            private int delay;
            private boolean cancelled;

            Entry(Runnable action, int delay, boolean repeating) {
                this.action = action;
                this.delay = delay;
                this.repeating = repeating;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }
}
