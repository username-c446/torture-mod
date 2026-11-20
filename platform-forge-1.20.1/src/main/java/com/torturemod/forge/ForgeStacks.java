package com.torturemod.forge;

import com.torturemod.api.model.ItemStackData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Перевод стопок между игрой и ядром. Данные стопки хранятся как SNBT: это самая зависимая от версии часть. */
final class ForgeStacks {
    private ForgeStacks() {
    }

    static Optional<ItemStackData> encode(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        String id = ForgeItems.id(stack).orElseThrow();
        CompoundTag tag = stack.getTag();
        return Optional.of(new ItemStackData(id, stack.getCount(), tag == null ? "" : tag.toString()));
    }

    /** Стопка ядра может быть больше игровой: она делится на несколько. */
    static List<ItemStack> decode(ItemStackData data) {
        var custom = ForgeCustomItems.get(data.itemId());
        if (custom.isPresent() && data.data().isEmpty()) {
            List<ItemStack> stacks = new ArrayList<>();
            ItemStack sample = ForgeCustomItems.create(custom.get(), 1);
            int max = Math.max(1, sample.getMaxStackSize());
            for (int left = data.count(); left > 0; left -= max) {
                stacks.add(ForgeCustomItems.create(custom.get(), Math.min(max, left)));
            }
            return stacks;
        }
        ResourceLocation id = ResourceLocation.tryParse(data.itemId());
        Item item = custom.isPresent() ? ForgeCustomItems.baseItem(custom.get())
                : id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
        List<ItemStack> stacks = new ArrayList<>();
        if (item == Items.AIR) {
            return stacks;
        }
        CompoundTag tag = null;
        if (!data.data().isEmpty()) {
            try {
                tag = TagParser.parseTag(data.data());
            } catch (Exception e) {
                tag = null;
            }
        }
        int left = data.count();
        int max = Math.max(1, new ItemStack(item).getMaxStackSize());
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(max, left));
            if (tag != null) {
                stack.setTag(tag.copy());
            }
            stacks.add(stack);
            left -= stack.getCount();
        }
        return stacks;
    }
}
