package com.torturemod.forge;

import com.mojang.authlib.GameProfile;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Targets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ForgeTargets implements Targets {
    private final MinecraftServer server;
    private final ForgeEntities entities;

    ForgeTargets(MinecraftServer server, ForgeEntities entities) {
        this.server = server;
        this.entities = entities;
    }

    @Override
    public Optional<TargetState> state(TargetId id) {
        return living(id).map(ForgeTargets::toState);
    }

    @Override
    public List<TargetState> livingIn(String worldId, BlockLocation min, BlockLocation max) {
        ServerLevel level = ForgeWorlds.level(server, worldId);
        if (level == null) {
            return List.of();
        }
        AABB box = new AABB(min.x(), min.y(), min.z(), max.x() + 1, max.y() + 1, max.z() + 1);
        return level.getEntitiesOfClass(LivingEntity.class, box).stream().map(ForgeTargets::toState).toList();
    }

    @Override
    public List<TargetState> playersNear(Location center, double radius) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> ForgeWorlds.id(player.level()).equals(center.worldId()))
                .filter(player -> player.distanceToSqr(center.x(), center.y(), center.z()) <= radius * radius)
                .map(ForgeTargets::toState)
                .toList();
    }

    @Override
    public Optional<TargetId> resolvePlayer(String nameOrUuid) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(nameOrUuid);
        if (online != null) {
            return Optional.of(new TargetId(online.getUUID()));
        }
        GameProfileCache cache = server.getProfileCache();
        try {
            UUID uuid = UUID.fromString(nameOrUuid);
            if (server.getPlayerList().getPlayer(uuid) != null || cache != null && cache.get(uuid).isPresent()) {
                return Optional.of(new TargetId(uuid));
            }
            return Optional.empty();
        } catch (IllegalArgumentException notUuid) {
            return cache == null ? Optional.empty() : cache.get(nameOrUuid).map(profile -> new TargetId(profile.getId()));
        }
    }

    @Override
    public Optional<String> playerName(TargetId player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player.value());
        if (online != null) {
            return Optional.of(online.getGameProfile().getName());
        }
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? Optional.empty() : cache.get(player.value()).map(GameProfile::getName);
    }

    @Override
    public void setPersistent(TargetId id) {
        living(id).ifPresent(entity -> {
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
        });
    }

    @Override
    public boolean canSee(TargetId observer, TargetId target) {
        Optional<LivingEntity> watcher = living(observer);
        Optional<LivingEntity> seen = living(target);
        return watcher.isPresent() && seen.isPresent() && watcher.get().level() == seen.get().level()
                && watcher.get().hasLineOfSight(seen.get());
    }

    private Optional<LivingEntity> living(TargetId id) {
        return entities.find(id);
    }

    static TargetState toState(LivingEntity living) {
        return new TargetState(
                ForgeEntities.idOf(living),
                living instanceof ServerPlayer ? TargetKind.PLAYER : TargetKind.MOB,
                BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString(),
                living.getName().getString(),
                ForgeWorlds.location(living.level(), living.getX(), living.getY(), living.getZ()),
                living.getHealth(),
                living.getMaxHealth());
    }
}
