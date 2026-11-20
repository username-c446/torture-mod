package com.torturemod.forge;

import com.torturemod.api.model.TargetId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Сущности по ID ядра. Тело вышедшего игрока отвечает за него под его ID. */
final class ForgeEntities {
    private final MinecraftServer server;
    private final ForgeBodies bodies;

    ForgeEntities(MinecraftServer server, ForgeBodies bodies) {
        this.server = server;
        this.bodies = bodies;
    }

    Optional<LivingEntity> find(TargetId id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id.value());
        if (player != null) {
            return Optional.of(player);
        }
        Optional<BodyPlayer> body = bodies.body(id.value());
        if (body.isPresent()) {
            return Optional.of(body.get());
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id.value());
            if (entity instanceof LivingEntity living) {
                return Optional.of(living);
            }
        }
        return Optional.empty();
    }

    /** Любая сущность, не только живая: лодка, вагонетка. */
    Optional<Entity> findAny(TargetId id) {
        Optional<LivingEntity> living = find(id);
        if (living.isPresent()) {
            return Optional.of(living.get());
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id.value());
            if (entity != null) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    static TargetId idOf(Entity entity) {
        return new TargetId(entity instanceof BodyPlayer body ? body.owner() : entity.getUUID());
    }
}
