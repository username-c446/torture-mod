package com.torturemod.forge;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/** Перевод мест между моделью ядра и миром игры. */
final class ForgeWorlds {
    private ForgeWorlds() {
    }

    static String id(Level level) {
        return level.dimension().location().toString();
    }

    static ServerLevel level(MinecraftServer server, String worldId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (id(level).equals(worldId)) {
                return level;
            }
        }
        return null;
    }

    static BlockLocation block(LevelAccessor level, BlockPos pos) {
        String world = level instanceof Level real ? id(real) : "unknown";
        return new BlockLocation(world, pos.getX(), pos.getY(), pos.getZ());
    }

    static BlockPos pos(BlockLocation location) {
        return new BlockPos(location.x(), location.y(), location.z());
    }

    static Location location(Level level, double x, double y, double z) {
        return new Location(id(level), x, y, z);
    }
}
