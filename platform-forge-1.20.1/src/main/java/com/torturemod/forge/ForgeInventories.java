package com.torturemod.forge;

import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.InventoryKind;
import com.torturemod.api.model.ItemEntry;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Inventories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class ForgeInventories implements Inventories {
    private final ForgeEntities entities;

    ForgeInventories(ForgeEntities entities) {
        this.entities = entities;
    }

    @Override
    public List<ItemEntry> contents(TargetId owner, InventoryKind kind) {
        // Для пленного не в сети вещи лежат на его теле.
        if (!(entities.find(owner).orElse(null) instanceof ServerPlayer player)) {
            return List.of();
        }
        List<ItemEntry> entries = new ArrayList<>();
        if (kind == InventoryKind.MAIN) {
            for (int slot : ForgeItems.playerSlots()) {
                add(entries, slot, player.getInventory().getItem(slot));
            }
        } else {
            Container ender = player.getEnderChestInventory();
            for (int slot = 0; slot < ender.getContainerSize(); slot++) {
                add(entries, slot, ender.getItem(slot));
            }
        }
        return entries;
    }

    @Override
    public boolean take(TargetId taker, TargetId owner, InventoryKind kind, int slot) {
        // Изымать вещи пленного решено не давать: палач видит только список.
        return false;
    }

    @Override
    public void give(TargetId owner, List<ItemStackData> stacks) {
        if (!(entities.find(owner).orElse(null) instanceof ServerPlayer player)) {
            return;
        }
        for (ItemStackData data : stacks) {
            for (ItemStack stack : ForgeStacks.decode(data)) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        player.inventoryMenu.sendAllDataToRemote();
    }

    private static void add(List<ItemEntry> entries, int slot, ItemStack stack) {
        ForgeItems.id(stack).ifPresent(id -> entries.add(new ItemEntry(slot, id, stack.getCount())));
    }
}
