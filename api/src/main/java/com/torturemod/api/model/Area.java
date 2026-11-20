package com.torturemod.api.model;

/** Параллелепипед из блоков, границы включительно. */
public record Area(String worldId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Area {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min must not exceed max");
        }
    }

    /** Область между двумя углами в любом порядке. */
    public static Area between(BlockLocation a, BlockLocation b) {
        if (!a.worldId().equals(b.worldId())) {
            throw new IllegalArgumentException("corners are in different worlds");
        }
        return new Area(a.worldId(), Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public boolean contains(BlockLocation block) {
        return block.worldId().equals(worldId)
                && block.x() >= minX && block.x() <= maxX
                && block.y() >= minY && block.y() <= maxY
                && block.z() >= minZ && block.z() <= maxZ;
    }

    public boolean contains(Location location) {
        return contains(BlockLocation.of(location));
    }

    public boolean contains(Area other) {
        return other.worldId.equals(worldId) && other.minX >= minX && other.maxX <= maxX
                && other.minY >= minY && other.maxY <= maxY && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public boolean intersects(Area other) {
        return other.worldId.equals(worldId) && other.minX <= maxX && other.maxX >= minX
                && other.minY <= maxY && other.maxY >= minY && other.minZ <= maxZ && other.maxZ >= minZ;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public Location center() {
        return new Location(worldId, (minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0);
    }

    /** Расстояние от точки до ближайшей точки области; ноль внутри. */
    public double distanceTo(Location location) {
        if (!location.worldId().equals(worldId)) {
            return Double.MAX_VALUE;
        }
        double dx = Math.max(Math.max(minX - location.x(), 0), location.x() - (maxX + 1));
        double dy = Math.max(Math.max(minY - location.y(), 0), location.y() - (maxY + 1));
        double dz = Math.max(Math.max(minZ - location.z(), 0), location.z() - (maxZ + 1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
