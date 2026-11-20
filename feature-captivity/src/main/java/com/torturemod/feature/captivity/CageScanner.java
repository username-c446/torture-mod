package com.torturemod.feature.captivity;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.port.BlockQueries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Ищет замкнутую клетку: обходит проходимые блоки от стартового, пока не упрётся в стены.
 * Клетка открыта, если объём превысил предел: значит, где-то есть выход.
 */
final class CageScanner {
    enum Status {
        CLOSED,
        OPEN,
        UNLOADED
    }

    record Result(Status status, boolean markerFound, Set<BlockLocation> interior) {
        boolean isCell() {
            return status == Status.CLOSED && markerFound;
        }
    }

    private static final Result OPEN = new Result(Status.OPEN, false, Set.of());
    private static final Result UNLOADED = new Result(Status.UNLOADED, false, Set.of());

    private CageScanner() {
    }

    static Result scan(BlockQueries blocks, BlockLocation start, CaptivitySettings settings) {
        Optional<String> startId = blocks.blockId(start);
        if (startId.isEmpty()) {
            return UNLOADED;
        }
        if (isBoundary(startId.get(), settings)) {
            return OPEN;
        }
        Set<BlockLocation> interior = new HashSet<>();
        ArrayDeque<BlockLocation> queue = new ArrayDeque<>();
        interior.add(start);
        queue.add(start);
        boolean markerFound = false;
        while (!queue.isEmpty()) {
            for (BlockLocation next : queue.poll().neighbors()) {
                if (interior.contains(next)) {
                    continue;
                }
                Optional<String> id = blocks.blockId(next);
                if (id.isEmpty()) {
                    return UNLOADED;
                }
                if (settings.markers().contains(id.get())) {
                    markerFound = true;
                    continue;
                }
                if (settings.walls().contains(id.get())) {
                    continue;
                }
                if (interior.size() >= settings.maxCellVolume()) {
                    return OPEN;
                }
                interior.add(next);
                queue.add(next);
            }
        }
        return new Result(Status.CLOSED, markerFound, Set.copyOf(interior));
    }

    static boolean isBoundary(String blockId, CaptivitySettings settings) {
        return settings.walls().contains(blockId) || settings.markers().contains(blockId);
    }
}
