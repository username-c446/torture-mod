package com.torturemod.forge;

import com.torturemod.api.model.Book;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Feedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;

final class ForgeFeedback implements Feedback {
    private final MinecraftServer server;
    private final Map<String, ServerBossEvent> bars = new HashMap<>();

    ForgeFeedback(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void message(TargetId receiver, String text) {
        ServerPlayer player = server.getPlayerList().getPlayer(receiver.value());
        if (player != null) {
            player.sendSystemMessage(Component.literal(text));
        }
    }

    @Override
    public void actionBar(TargetId receiver, String text) {
        ServerPlayer player = server.getPlayerList().getPlayer(receiver.value());
        if (player != null) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }

    @Override
    public void sound(Location location, String soundId) {
        ServerLevel level = ForgeWorlds.level(server, location.worldId());
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
        if (level != null && sound != null) {
            level.playSound(null, location.x(), location.y(), location.z(), sound, SoundSource.PLAYERS, 1, 1);
        }
    }

    @Override
    public void particles(Location location, String particleId, int count) {
        ServerLevel level = ForgeWorlds.level(server, location.worldId());
        ResourceLocation id = ResourceLocation.tryParse(particleId);
        // Без параметров отправляются только простые частицы: пыль, предметы и блоки требуют данных.
        if (level != null && id != null && BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType particle) {
            level.sendParticles(particle, location.x(), location.y() + 1, location.z(), count, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @Override
    public void particleFor(TargetId viewer, Location location, String particleId) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.value());
        ResourceLocation id = ResourceLocation.tryParse(particleId);
        if (player == null || id == null || !ForgeWorlds.id(player.level()).equals(location.worldId())
                || !(BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType particle)) {
            return;
        }
        player.serverLevel().sendParticles(player, particle, true, location.x(), location.y(), location.z(), 1, 0, 0, 0, 0);
    }

    @Override
    public void openBook(TargetId viewer, Book book) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.value());
        if (player != null) {
            ForgeBooks.open(player, book);
        }
    }

    @Override
    public void showBar(TargetId viewer, String key, String title, float progress) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewer.value());
        if (player == null) {
            return;
        }
        ServerBossEvent bar = bars.computeIfAbsent(viewer.value() + "|" + key, ignored -> new ServerBossEvent(
                Component.literal(title), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(Component.literal(title));
        bar.setProgress(Math.max(0, Math.min(1, progress)));
        bar.addPlayer(player);
    }

    @Override
    public void hideBar(TargetId viewer, String key) {
        ServerBossEvent bar = bars.remove(viewer.value() + "|" + key);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }
}
