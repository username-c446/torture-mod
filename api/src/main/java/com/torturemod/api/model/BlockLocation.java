package com.torturemod.api.model;

import java.util.List;

public record BlockLocation(String worldId, int x, int y, int z) {
    /** Блок, в котором находится точка. */
    public static BlockLocation of(Location location) {
        return new BlockLocation(location.worldId(),
                (int) Math.floor(location.x()), (int) Math.floor(location.y()), (int) Math.floor(location.z()));
    }

    public BlockLocation offset(int dx, int dy, int dz) {
        return new BlockLocation(worldId, x + dx, y + dy, z + dz);
    }

    public List<BlockLocation> neighbors() {
        return List.of(offset(1, 0, 0), offset(-1, 0, 0), offset(0, 1, 0),
                offset(0, -1, 0), offset(0, 0, 1), offset(0, 0, -1));
    }
}
