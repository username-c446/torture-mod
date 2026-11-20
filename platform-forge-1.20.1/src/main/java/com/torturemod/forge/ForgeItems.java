package com.torturemod.forge;

import com.torturemod.api.items.ShapedRecipe;
import com.torturemod.api.items.CustomItem;
import com.torturemod.api.model.ItemTraits;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.material.Fluids;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

final class ForgeItems implements Items {
    private final MinecraftServer server;

    ForgeItems(MinecraftServer server) {
        this.server = server;
    }

    static Optional<String> id(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        // Предмет мода — ванильная стопка с меткой: ядро видит его под своим ID.
        return ForgeCustomItems.idOf(stack).or(() -> Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }

    @Override
    public Optional<ItemTraits> traits(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        Set<String> tags = new HashSet<>();
        item.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
        double damage = 1 + item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)
                .stream().mapToDouble(AttributeModifier::getAmount).sum();
        return Optional.of(new ItemTraits(itemId, id.getNamespace(), Set.copyOf(tags), kinds(item), damage));
    }

    /** Категории по классам предмета: так модовые предметы определяются без настройки. */
    private static Set<String> kinds(Item item) {
        Set<String> kinds = new HashSet<>();
        if (item instanceof SwordItem) {
            kinds.add("sword");
        }
        if (item instanceof AxeItem) {
            kinds.add("axe");
        }
        if (item instanceof PickaxeItem) {
            kinds.add("pickaxe");
        }
        if (item instanceof ShovelItem) {
            kinds.add("shovel");
        }
        if (item instanceof HoeItem) {
            kinds.add("hoe");
        }
        if (item instanceof DiggerItem) {
            kinds.add("tool");
        }
        if (item instanceof TridentItem) {
            kinds.add("trident");
        }
        if (item instanceof FlintAndSteelItem || item instanceof FireChargeItem) {
            kinds.add("igniter");
        }
        if (item instanceof BucketItem bucket) {
            if (bucket.getFluid().isSame(Fluids.WATER)) {
                kinds.add("water_bucket");
            } else if (bucket.getFluid().isSame(Fluids.LAVA)) {
                kinds.add("lava_bucket");
            }
        }
        if (item instanceof PotionItem) {
            kinds.add("potion");
        }
        FoodProperties food = item.getFoodProperties();
        if (food != null && food.getEffects().stream()
                .anyMatch(pair -> pair.getFirst().getEffect().getCategory() == MobEffectCategory.HARMFUL)) {
            kinds.add("harmful_food");
        }
        return Set.copyOf(kinds);
    }

    @Override
    public Iterable<String> allItemIds() {
        return BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString).sorted().toList();
    }

    @Override
    public Optional<String> heldItem(TargetId actor) {
        ServerPlayer player = server.getPlayerList().getPlayer(actor.value());
        return player == null ? Optional.empty() : id(player.getMainHandItem());
    }

    @Override
    public OptionalInt findInHands(TargetId actor, String itemId) {
        ServerPlayer player = server.getPlayerList().getPlayer(actor.value());
        if (player == null) {
            return OptionalInt.empty();
        }
        if (id(player.getMainHandItem()).filter(itemId::equals).isPresent()) {
            return OptionalInt.of(player.getInventory().selected);
        }
        return id(player.getOffhandItem()).filter(itemId::equals).isPresent()
                ? OptionalInt.of(OFFHAND_SLOT)
                : OptionalInt.empty();
    }

    @Override
    public void consume(TargetId actor, String itemId, int count) {
        ServerPlayer player = server.getPlayerList().getPlayer(actor.value());
        if (player == null || player.getAbilities().instabuild) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (id(stack).filter(itemId::equals).isPresent()) {
                stack.shrink(count);
                return;
            }
        }
    }

    @Override
    public void spend(TargetId actor, int slot) {
        ServerPlayer player = server.getPlayerList().getPlayer(actor.value());
        if (player == null || player.getAbilities().instabuild) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        if (stack.isDamageableItem()) {
            if (stack.hurt(1, player.getRandom(), player)) {
                stack.shrink(1);
                stack.setDamageValue(0);
            }
        } else if (stack.getItem() instanceof BucketItem bucket && !bucket.getFluid().isSame(Fluids.EMPTY)) {
            player.getInventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.BUCKET));
        } else if (stack.is(net.minecraft.world.item.Items.POTION)) {
            player.getInventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
        } else {
            stack.shrink(1);
        }
        player.inventoryMenu.sendAllDataToRemote();
    }

    @Override
    public void defineCustomItem(CustomItem item) {
        ForgeCustomItems.define(item);
    }

    @Override
    public void addRecipe(ShapedRecipe recipe) {
        ForgeCustomItems.addRecipe(server, recipe);
    }

    @Override
    public boolean isFood(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id).isEdible();
    }

    @Override
    public void giveMap(TargetId actor, String name, int[] argb128) {
        ServerPlayer player = server.getPlayerList().getPlayer(actor.value());
        if (player != null) {
            ForgeMaps.give(server, player, name, argb128);
        }
    }

    static List<Integer> playerSlots() {
        List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < 36; i++) {
            slots.add(i);
        }
        slots.add(OFFHAND_SLOT);
        return slots;
    }
}
