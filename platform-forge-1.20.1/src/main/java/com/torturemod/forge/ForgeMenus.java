package com.torturemod.forge;

import java.util.List;
import java.util.ArrayList;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Menus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** Меню на ванильном сундуке: клиенту без мода оно видно как обычный сундук, но вещи из него не берутся. */
final class ForgeMenus implements Menus {
    private static final MenuType<?>[] TYPES = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

    private final MinecraftServer server;

    ForgeMenus(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void open(TargetId viewer, Menu menu) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.value());
        if (player == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, menu.rows()));
        SimpleContainer container = new SimpleContainer(rows * 9);
        Map<Integer, Runnable> actions = new HashMap<>();
        for (Entry entry : menu.entries()) {
            if (entry.slot() < 0 || entry.slot() >= rows * 9) {
                continue;
            }
            container.setItem(entry.slot(), icon(entry));
            if (entry.onClick() != null) {
                actions.put(entry.slot(), entry.onClick());
            }
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new LockedMenu(TYPES[rows - 1], id, inventory, container, rows, actions, server),
                Component.literal(menu.title())));
    }

    @Override
    public void openStorage(TargetId viewer, StorageMenu menu) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.value());
        if (player == null) {
            server.execute(() -> menu.onClose().accept(List.of()));
            return;
        }
        int rows = Math.max(1, Math.min(6, menu.rows()));
        SimpleContainer container = new SimpleContainer(rows * 9);
        int slot = 0;
        for (ItemStackData data : menu.contents()) {
            for (ItemStack stack : ForgeStacks.decode(data)) {
                if (slot < rows * 9) {
                    container.setItem(slot++, stack);
                }
            }
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new StorageChestMenu(TYPES[rows - 1], id, inventory, container, rows, menu, server),
                Component.literal(menu.title())));
    }

    /** Обычный сундук; при закрытии содержимое уходит фиче, а меню пустеет, чтобы ничего не удвоилось. */
    private static final class StorageChestMenu extends ChestMenu {
        private final SimpleContainer container;
        private final StorageMenu menu;
        private final MinecraftServer server;
        private boolean closed;

        StorageChestMenu(MenuType<?> type, int id, Inventory inventory, SimpleContainer container, int rows,
                         StorageMenu menu, MinecraftServer server) {
            super(type, id, inventory, container, rows);
            this.container = container;
            this.menu = menu;
            this.server = server;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (closed) {
                return;
            }
            closed = true;
            List<ItemStackData> contents = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ForgeStacks.encode(container.removeItemNoUpdate(slot)).ifPresent(contents::add);
            }
            server.execute(() -> menu.onClose().accept(contents));
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    @Override
    public void close(TargetId viewer) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.value());
        if (player != null) {
            player.closeContainer();
        }
    }

    private static ItemStack icon(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.itemId());
        ItemStack stack = new ItemStack(id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id) : Items.BARRIER);
        stack.setHoverName(Component.literal(entry.label()).withStyle(style -> style.withItalic(false)));
        if (!entry.lore().isEmpty()) {
            CompoundTag display = stack.getOrCreateTagElement("display");
            ListTag lore = new ListTag();
            for (String line : entry.lore()) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(
                        Component.literal(line).withStyle(style -> style.withItalic(false)))));
            }
            display.put("Lore", lore);
        }
        return stack;
    }

    private static final class LockedMenu extends ChestMenu {
        private final int size;
        private final Map<Integer, Runnable> actions;
        private final MinecraftServer server;

        LockedMenu(MenuType<?> type, int id, Inventory inventory, SimpleContainer container, int rows,
                   Map<Integer, Runnable> actions, MinecraftServer server) {
            super(type, id, inventory, container, rows);
            this.size = rows * 9;
            this.actions = actions;
            this.server = server;
        }

        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            // Ни одно нажатие не перекладывает вещи: клиент получает настоящее содержимое обратно.
            Runnable action = slot >= 0 && slot < size ? actions.get(slot) : null;
            sendAllDataToRemote();
            if (action != null && clickType == ClickType.PICKUP) {
                // Действие может открыть новое меню или закрыть это, поэтому выполняем его после обработки нажатия.
                server.execute(action);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
