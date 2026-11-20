package com.torturemod.forge;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Соединение тела: игра отправляет ему пакеты как игроку, а они никуда не уходят. */
final class BodyConnection extends ServerGamePacketListenerImpl {
    BodyConnection(MinecraftServer server, ServerPlayer body) {
        super(server, new Connection(PacketFlow.CLIENTBOUND), body);
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
    }

    @Override
    public void tick() {
    }

    @Override
    public void disconnect(Component reason) {
    }
}
