package com.torturemod.forge;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * ЛКМ в воздух сервер видит только как взмах руки: Forge не даёт для него события на сервере.
 * Обработчик в канале игрока замечает пакет взмаха и передаёт его на основной поток.
 */
final class SwingWatcher extends ChannelDuplexHandler {
    static final String NAME = "torturemod_swing";

    private final MinecraftServer server;
    private final ServerPlayer player;
    private final Consumer<ServerPlayer> onSwing;

    SwingWatcher(MinecraftServer server, ServerPlayer player, Consumer<ServerPlayer> onSwing) {
        this.server = server;
        this.player = player;
        this.onSwing = onSwing;
    }

    static void attach(MinecraftServer server, ServerPlayer player, Consumer<ServerPlayer> onSwing) {
        var pipeline = player.connection.connection.channel().pipeline();
        if (pipeline.get(NAME) == null && pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", NAME, new SwingWatcher(server, player, onSwing));
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (message instanceof ServerboundSwingPacket) {
            server.execute(() -> onSwing.accept(player));
        }
        super.channelRead(context, message);
    }
}
