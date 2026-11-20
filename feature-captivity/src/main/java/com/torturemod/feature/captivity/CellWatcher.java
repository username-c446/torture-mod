package com.torturemod.feature.captivity;

import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Следит, что клетки целы, а пленные внутри. Всё на одном общем тике. */
final class CellWatcher {
    private final FeatureContext context;
    private final CaptivityRegistry registry;
    private final Supplier<CaptivitySettings> settings;
    /** Внутренность клетки каждого пленного. Пусто, пока клетку не удалось проверить: например, чанк не загружен. */
    private final Map<TargetId, Set<BlockLocation>> interiors = new HashMap<>();
    private final Set<TargetId> pendingRescan = new LinkedHashSet<>();
    private long ticks;

    CellWatcher(FeatureContext context, CaptivityRegistry registry, Supplier<CaptivitySettings> settings) {
        this.context = context;
        this.registry = registry;
        this.settings = settings;
    }

    void remember(TargetId captive, Set<BlockLocation> interior) {
        interiors.put(captive, interior);
    }

    void forget(TargetId captive) {
        interiors.remove(captive);
        pendingRescan.remove(captive);
    }

    /** Блок рядом с клеткой изменился: клетку проверяем на следующем тике, когда изменение уже применено. */
    void onBlockChanged(BlockLocation block) {
        interiors.forEach((captive, interior) -> {
            if (interior.contains(block) || block.neighbors().stream().anyMatch(interior::contains)) {
                pendingRescan.add(captive);
            }
        });
    }

    void tick() {
        ticks++;
        CaptivitySettings current = settings.get();
        if (!pendingRescan.isEmpty()) {
            List<TargetId> due = List.copyOf(pendingRescan);
            pendingRescan.clear();
            due.forEach(this::rescan);
        }
        if (ticks % current.cellCheckTicks() == 0) {
            caged().forEach(record -> rescan(record.target()));
        }
        if (ticks % current.positionCheckTicks() == 0) {
            caged().forEach(this::checkPosition);
        }
    }

    private List<CaptivityRecord> caged() {
        return registry.records().stream()
                .filter(record -> record.cell().isPresent() && !registry.isClaimed(record.target())).toList();
    }

    private void rescan(TargetId captive) {
        if (registry.isSuspended(captive) || registry.isClaimed(captive)) {
            return;
        }
        Optional<BlockLocation> anchor = registry.record(captive).flatMap(CaptivityRecord::cell);
        if (anchor.isEmpty()) {
            return;
        }
        CageScanner.Result result = CageScanner.scan(context.platform().blocks(), anchor.get(), settings.get());
        if (result.status() == CageScanner.Status.UNLOADED) {
            return;
        }
        if (result.isCell()) {
            interiors.put(captive, result.interior());
        } else {
            registry.release(captive, ReleaseReason.CELL_BROKEN);
        }
    }

    private void checkPosition(CaptivityRecord record) {
        if (registry.isSuspended(record.target())) {
            return;
        }
        Optional<TargetState> state = context.platform().targets().state(record.target());
        if (state.isEmpty()) {
            return;
        }
        if (!interiors.containsKey(record.target())) {
            rescan(record.target());
        }
        Set<BlockLocation> interior = interiors.get(record.target());
        if (interior != null && !interior.contains(BlockLocation.of(state.get().location()))) {
            registry.release(record.target(), ReleaseReason.LEFT_CELL);
        }
    }
}
