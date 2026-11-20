package com.torturemod.forge;

import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Convoy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Поводок через невидимую опору и посадку в транспорт средствами игры. */
final class ForgeConvoy implements Convoy {
    private final ForgeEntities entities;
    private final Map<UUID, LeashAnchor> anchors = new HashMap<>();

    ForgeConvoy(ForgeEntities entities) {
        this.entities = entities;
    }

    @Override
    public void showLeash(TargetId captive, TargetId holder) {
        hideLeash(captive);
        Optional<LivingEntity> living = entities.find(captive);
        Optional<Entity> holderEntity = entities.findAny(holder);
        if (living.isEmpty() || holderEntity.isEmpty()) {
            return;
        }
        LeashAnchor anchor = new LeashAnchor(living.get().level());
        place(anchor, living.get());
        living.get().level().addFreshEntity(anchor);
        anchor.setLeashedTo(holderEntity.get(), true);
        anchors.put(captive.value(), anchor);
    }

    @Override
    public void hideLeash(TargetId captive) {
        LeashAnchor anchor = anchors.remove(captive.value());
        if (anchor != null) {
            anchor.dropLeash(true, false);
            anchor.discard();
        }
    }

    /** Опора следует за пленным; если он пропал или сменил мир, верёвка убирается. */
    void tick() {
        for (Map.Entry<UUID, LeashAnchor> entry : List.copyOf(anchors.entrySet())) {
            Optional<LivingEntity> living = entities.find(new TargetId(entry.getKey()));
            LeashAnchor anchor = entry.getValue();
            if (living.isEmpty() || living.get().level() != anchor.level() || anchor.isRemoved()) {
                hideLeash(new TargetId(entry.getKey()));
            } else {
                place(anchor, living.get());
            }
        }
    }

    void removeAll() {
        List.copyOf(anchors.keySet()).forEach(owner -> hideLeash(new TargetId(owner)));
    }

    @Override
    public void push(TargetId target, double vx, double vy, double vz) {
        entities.find(target).ifPresent(living -> {
            living.setDeltaMovement(vx, vy, vz);
            // Игрок двигается на своём клиенте: без этого флага сервер не отправит ему новую скорость.
            living.hurtMarked = true;
        });
    }

    @Override
    public boolean isVehicle(TargetId entity) {
        return entities.findAny(entity)
                .filter(found -> found instanceof Boat || found instanceof Minecart || found instanceof AbstractHorse)
                .isPresent();
    }

    @Override
    public boolean mount(TargetId rider, TargetId vehicle) {
        Optional<LivingEntity> living = entities.find(rider);
        Optional<Entity> ride = entities.findAny(vehicle);
        if (living.isEmpty() || ride.isEmpty() || ride.get().getPassengers().size() >= maxPassengers(ride.get())) {
            return false;
        }
        return living.get().startRiding(ride.get(), true);
    }

    @Override
    public void dismount(TargetId rider) {
        entities.find(rider).ifPresent(Entity::stopRiding);
    }

    @Override
    public Optional<TargetId> vehicleOf(TargetId rider) {
        return entities.find(rider).map(Entity::getVehicle).map(ForgeEntities::idOf);
    }

    private static int maxPassengers(Entity vehicle) {
        return vehicle instanceof Boat ? 2 : 1;
    }

    private static void place(LeashAnchor anchor, LivingEntity living) {
        anchor.moveTo(living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ());
    }
}
