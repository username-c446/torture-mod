package com.torturemod.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Тело вышедшего пленного: настоящий серверный игрок без соединения. Получает урон и эффекты, умирает
 * и роняет вещи как игрок. У тела свой UUID, иначе зашедший владелец не смог бы появиться в мире.
 */
final class BodyPlayer extends ServerPlayer {
    private final UUID owner;

    BodyPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, UUID owner) {
        super(server, level, profile);
        this.owner = owner;
        // Пакеты телу никуда не уходят.
        this.connection = new BodyConnection(server, this);
    }

    UUID owner() {
        return owner;
    }
}
