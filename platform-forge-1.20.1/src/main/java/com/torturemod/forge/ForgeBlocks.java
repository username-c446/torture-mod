package com.torturemod.forge;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.port.BlockQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

final class ForgeBlocks implements BlockQueries {
    private final MinecraftServer server;

    ForgeBlocks(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Optional<String> blockId(BlockLocation location) {
        ServerLevel level = ForgeWorlds.level(server, location.worldId());
        if (level == null) {
            return Optional.empty();
        }
        BlockPos pos = ForgeWorlds.pos(location);
        // Проверка не загружает чанк: незагруженное место считается неизвестным.
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }
}
