package com.torturemod.forge;

import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Permissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Пока без системы прав: право использования есть у всех, остальные права у операторов. */
final class ForgePermissions implements Permissions {
    private static final int OPERATOR_LEVEL = 2;

    private final MinecraftServer server;

    ForgePermissions(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean has(TargetId player, String node) {
        if (USE.equals(node)) {
            return true;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player.value());
        return online != null && online.hasPermissions(OPERATOR_LEVEL);
    }
}
