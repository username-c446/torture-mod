package com.torturemod.forge;

import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Minions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Слуги тюрьмы. У подчинённого моба убираются все цели выбора противника: так охранник не нападает
 * на команду сам, а атакует только тех, кого назначит фича. Мозг жителей ходит по цели прогулки.
 */
final class ForgeMinions implements Minions {
    private final ForgeEntities entities;
    /** Уже подчинённые экземпляры: после перезагрузки чанка моб приходит новым экземпляром. */
    private final Set<Mob> enslaved = Collections.newSetFromMap(new WeakHashMap<>());

    ForgeMinions(ForgeEntities entities) {
        this.entities = entities;
    }

    private Optional<Mob> mob(TargetId id) {
        return entities.find(id).filter(Mob.class::isInstance).map(Mob.class::cast);
    }

    @Override
    public boolean enslave(TargetId id) {
        Optional<Mob> mob = mob(id);
        if (mob.isEmpty()) {
            return false;
        }
        if (enslaved.add(mob.get())) {
            mob.get().targetSelector.removeAllGoals(goal -> true);
            mob.get().setTarget(null);
            mob.get().setPersistenceRequired();
        }
        return true;
    }

    @Override
    public void free(TargetId id) {
        // Цели выбора противника вернутся, когда моб загрузится заново: игра создаст его с обычным поведением.
        mob(id).ifPresent(mob -> {
            enslaved.remove(mob);
            mob.setTarget(null);
        });
    }

    @Override
    public void moveTo(TargetId id, Location to, double speed) {
        mob(id).ifPresent(mob -> {
            if (!ForgeWorlds.id(mob.level()).equals(to.worldId())) {
                return;
            }
            if (mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new Vec3(to.x(), to.y(), to.z()), (float) (speed * 0.5), 1));
            } else {
                mob.getNavigation().moveTo(to.x(), to.y(), to.z(), speed);
            }
        });
    }

    @Override
    public void setAttackTarget(TargetId id, Optional<TargetId> target) {
        mob(id).ifPresent(mob -> {
            LivingEntity living = target.flatMap(entities::find).orElse(null);
            if (mob.getTarget() != living) {
                mob.setTarget(living);
            }
        });
    }

    @Override
    public void setLabel(TargetId id, String label) {
        mob(id).ifPresent(mob -> {
            if (label.isEmpty()) {
                if (mob.hasCustomName()) {
                    mob.setCustomName(null);
                    mob.setCustomNameVisible(false);
                }
                return;
            }
            if (mob.getCustomName() == null || !mob.getCustomName().getString().equals(label)) {
                mob.setCustomName(Component.literal(label));
                mob.setCustomNameVisible(true);
            }
        });
    }

    @Override
    public void setHeld(TargetId id, Optional<ItemStackData> stack) {
        mob(id).ifPresent(mob -> {
            ItemStack item = stack.flatMap(data -> ForgeStacks.decode(data).stream().findFirst()).orElse(ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.MAINHAND, item);
            // Ношу фича хранит сама и роняет её при гибели курьера, поэтому предмет в руке не выпадает.
            mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        });
    }

    @Override
    public boolean equipFromHand(TargetId playerId, TargetId id) {
        Optional<Mob> mob = mob(id);
        Optional<LivingEntity> player = entities.find(playerId);
        if (mob.isEmpty() || !(player.orElse(null) instanceof ServerPlayer owner)) {
            return false;
        }
        ItemStack hand = owner.getMainHandItem();
        if (hand.isEmpty()) {
            return false;
        }
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(hand);
        boolean weapon = hand.getItem() instanceof SwordItem || hand.getItem() instanceof AxeItem
                || hand.getItem() instanceof ProjectileWeaponItem || hand.getItem() instanceof TridentItem;
        boolean armor = slot.getType() == EquipmentSlot.Type.ARMOR || hand.getItem() instanceof ShieldItem;
        if (!weapon && !armor) {
            return false;
        }
        ItemStack old = mob.get().getItemBySlot(slot);
        mob.get().setItemSlot(slot, hand.copy());
        // Снаряжение от игрока всегда выпадает при гибели охранника.
        mob.get().setDropChance(slot, 2.0f);
        owner.setItemInHand(InteractionHand.MAIN_HAND, old.copy());
        owner.inventoryMenu.sendAllDataToRemote();
        return true;
    }

    @Override
    public Map<String, String> equipment(TargetId id) {
        Map<String, String> equipment = new LinkedHashMap<>();
        mob(id).ifPresent(mob -> {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = mob.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    equipment.put(slot.getName(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        });
        return equipment;
    }

    @Override
    public List<ItemStackData> inventory(TargetId id) {
        return mob(id).filter(InventoryCarrier.class::isInstance).map(mob -> {
            SimpleContainer inventory = ((InventoryCarrier) mob).getInventory();
            List<ItemStackData> stacks = new java.util.ArrayList<>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ForgeStacks.encode(inventory.getItem(slot)).ifPresent(stacks::add);
            }
            return stacks;
        }).orElse(List.of());
    }

    @Override
    public int removeFromInventory(TargetId id, String itemId, int count) {
        Optional<Mob> mob = mob(id).filter(InventoryCarrier.class::isInstance);
        if (mob.isEmpty()) {
            return 0;
        }
        SimpleContainer inventory = ((InventoryCarrier) mob.get()).getInventory();
        int removed = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && removed < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ForgeStacks.encode(stack).filter(data -> data.itemId().equals(itemId)).isPresent()) {
                removed += inventory.removeItem(slot, Math.min(count - removed, stack.getCount())).getCount();
            }
        }
        return removed;
    }

    @Override
    public void befriend(TargetId villager, TargetId player) {
        mob(villager).filter(Villager.class::isInstance).map(Villager.class::cast)
                .ifPresent(found -> found.getGossips().add(player.value(), GossipType.MAJOR_POSITIVE, 20));
    }

    @Override
    public void drop(TargetId id, List<ItemStackData> stacks) {
        entities.findAny(id).ifPresent(entity -> stacks.forEach(data ->
                ForgeStacks.decode(data).forEach(entity::spawnAtLocation)));
    }

    @Override
    public Optional<String> profession(TargetId villager) {
        return mob(villager).filter(Villager.class::isInstance).map(Villager.class::cast)
                .map(found -> BuiltInRegistries.VILLAGER_PROFESSION.getKey(found.getVillagerData().getProfession()).toString());
    }
}
