package com.torturemod.forge;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Bodies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Тела пленных, которые не в сети. Состояние переносится через NBT игрока: при выходе оно копируется
 * в тело, при заходе возвращается владельцу, а при удалении тела записывается в его файл игрока.
 */
final class ForgeBodies implements Bodies {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private final Map<UUID, BodyPlayer> bodies = new HashMap<>();
    /** Профили со скином, запомненные при выходе: после перезапуска скин может быть стандартным. */
    private final Map<UUID, GameProfile> profiles = new HashMap<>();

    ForgeBodies(MinecraftServer server) {
        this.server = server;
    }

    Optional<BodyPlayer> body(UUID owner) {
        return Optional.ofNullable(bodies.get(owner));
    }

    @Override
    public boolean spawn(TargetId player) {
        UUID owner = player.value();
        if (bodies.containsKey(owner)) {
            return true;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        CompoundTag data;
        ServerLevel level;
        GameProfile source;
        if (online != null) {
            data = online.saveWithoutId(new CompoundTag());
            level = online.serverLevel();
            source = online.getGameProfile();
            profiles.put(owner, source);
        } else {
            data = readSaved(owner);
            if (data == null) {
                return false;
            }
            ServerLevel saved = ForgeWorlds.level(server, data.getString("Dimension"));
            level = saved != null ? saved : server.overworld();
            source = profiles.computeIfAbsent(owner, this::cachedProfile);
        }
        GameProfile profile = new GameProfile(bodyId(owner), source.getName());
        profile.getProperties().putAll(source.getProperties());
        BodyPlayer body = new BodyPlayer(server, level, profile, owner);
        data.remove("UUID");
        body.load(data);
        // Без записи в списке игроков клиент не покажет тело. Без UPDATE_LISTED его нет в Tab.
        server.getPlayerList().broadcastAll(info(List.of(body)));
        level.addNewPlayer(body);
        bodies.put(owner, body);
        return true;
    }

    @Override
    public boolean exists(TargetId player) {
        return bodies.containsKey(player.value());
    }

    @Override
    public void restore(TargetId player) {
        UUID owner = player.value();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        BodyPlayer body = bodies.get(owner);
        if (online == null || body == null) {
            return;
        }
        bodies.remove(owner);
        if (body.isDeadOrDying()) {
            // Смерть тела уже записана в файл игрока: при заходе он увидел экран смерти.
            despawn(body);
            return;
        }
        CompoundTag data = body.saveWithoutId(new CompoundTag());
        data.remove("UUID");
        ServerLevel level = body.serverLevel();
        double x = body.getX(), y = body.getY(), z = body.getZ();
        float yaw = body.getYRot(), pitch = body.getXRot();
        despawn(body);
        online.load(data);
        online.teleportTo(level, x, y, z, yaw, pitch);
        // Клиент знает только то, что сервер ему отправил: обновляем здоровье, вещи и эффекты.
        online.resetSentInfo();
        online.inventoryMenu.sendAllDataToRemote();
        for (MobEffectInstance effect : online.getActiveEffects()) {
            online.connection.send(new ClientboundUpdateMobEffectPacket(online.getId(), effect));
        }
    }

    @Override
    public void remove(TargetId player) {
        BodyPlayer body = bodies.get(player.value());
        // Умирающее тело ещё роняет вещи: его уберёт следующий тик, когда смерть закончится.
        if (body != null && !body.isDeadOrDying()) {
            finish(player.value());
        }
    }

    /**
     * Новому игроку нужны записи о телах. Игра показывает ему тела раньше, чем мод успевает их прислать,
     * и клиент такое появление пропускает, поэтому после записей отправляем появление тел заново.
     */
    void showTo(ServerPlayer viewer) {
        if (bodies.isEmpty()) {
            return;
        }
        viewer.connection.send(info(List.copyOf(bodies.values())));
        for (BodyPlayer body : bodies.values()) {
            if (body.level() == viewer.level()) {
                viewer.connection.send(new ClientboundAddPlayerPacket(body));
                List<SynchedEntityData.DataValue<?>> data = body.getEntityData().getNonDefaultValues();
                if (data != null) {
                    viewer.connection.send(new ClientboundSetEntityDataPacket(body.getId(), data));
                }
            }
        }
    }

    /** Тело — игрок без соединения, поэтому его тик запускаем сами. */
    void tick() {
        for (Map.Entry<UUID, BodyPlayer> entry : List.copyOf(bodies.entrySet())) {
            BodyPlayer body = entry.getValue();
            if (body.isDeadOrDying()) {
                finish(entry.getKey());
                continue;
            }
            try {
                body.doTick();
            } catch (RuntimeException e) {
                LOGGER.error("Body of {} failed to tick", entry.getKey(), e);
            }
        }
    }

    void removeAll() {
        List.copyOf(bodies.keySet()).forEach(this::finish);
    }

    private void finish(UUID owner) {
        BodyPlayer body = bodies.remove(owner);
        if (body == null) {
            return;
        }
        save(owner, body);
        despawn(body);
    }

    private void despawn(BodyPlayer body) {
        body.serverLevel().removePlayerImmediately(body, Entity.RemovalReason.DISCARDED);
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(body.getUUID())));
    }

    private static ClientboundPlayerInfoUpdatePacket info(List<ServerPlayer> players) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                players);
    }

    private File playerFile(UUID owner) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner + ".dat").toFile();
    }

    private CompoundTag readSaved(UUID owner) {
        File file = playerFile(owner);
        if (!file.exists()) {
            return null;
        }
        try {
            return NbtIo.readCompressed(file);
        } catch (IOException e) {
            LOGGER.error("Cannot read player data of {}", owner, e);
            return null;
        }
    }

    /** Записывает состояние тела в файл владельца так же, как игра сохраняет игрока: через временный файл. */
    private void save(UUID owner, BodyPlayer body) {
        CompoundTag data = body.saveWithoutId(new CompoundTag());
        data.putUUID("UUID", owner);
        NbtUtils.addCurrentDataVersion(data);
        File target = playerFile(owner);
        try {
            File temp = File.createTempFile(owner + "-", ".dat", target.getParentFile());
            NbtIo.writeCompressed(data, temp);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Cannot save the body of {}", owner, e);
        }
    }

    private GameProfile cachedProfile(UUID owner) {
        GameProfileCache cache = server.getProfileCache();
        Optional<GameProfile> cached = cache == null ? Optional.empty() : cache.get(owner);
        return cached.orElseGet(() -> new GameProfile(owner, owner.toString().substring(0, 16)));
    }

    private static UUID bodyId(UUID owner) {
        return UUID.nameUUIDFromBytes(("torturemod:body:" + owner).getBytes(StandardCharsets.UTF_8));
    }
}
