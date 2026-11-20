package com.torturemod.feature.captivity;

import com.torturemod.api.captivity.CaptureFailure;
import com.torturemod.api.captivity.CaptureMethod;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Захват в клетке: предмет захвата применяют к цели или к маркеру клетки. */
final class CaptureHandler {
    private final FeatureContext context;
    private final CaptivityRegistry registry;
    private final CellWatcher watcher;
    private final Supplier<CaptivitySettings> settings;

    CaptureHandler(FeatureContext context, CaptivityRegistry registry, CellWatcher watcher,
                   Supplier<CaptivitySettings> settings) {
        this.context = context;
        this.registry = registry;
        this.watcher = watcher;
        this.settings = settings;
    }

    void onEntityInteracted(GameEvent.EntityInteracted event) {
        Optional<String> item = captureItem(event.actor(), event.itemId(), event.cancellation().isCancelled());
        if (item.isEmpty()) {
            return;
        }
        // Пленного предмет захвата не берёт снова: поводком его ведёт фича конвоя.
        if (registry.isCaptive(event.target())) {
            return;
        }
        Optional<TargetState> target = context.platform().targets().state(event.target());
        if (target.isEmpty()) {
            return;
        }
        BlockLocation anchor = BlockLocation.of(target.get().location());
        CageScanner.Result cell = CageScanner.scan(context.platform().blocks(), anchor, settings.get());
        if (!cell.isCell()) {
            // Не клетка: обычное поведение предмета, например поводок на животном.
            return;
        }
        event.cancellation().cancel();
        tryCapture(event.actor(), target.get(), anchor, cell.interior(), item.get());
    }

    void onBlockInteracted(GameEvent.BlockInteracted event) {
        Optional<String> item = captureItem(event.actor(), event.itemId(), event.cancellation().isCancelled());
        if (item.isEmpty()) {
            return;
        }
        Optional<String> block = context.platform().blocks().blockId(event.block());
        if (block.isEmpty() || !settings.get().markers().contains(block.get())) {
            return;
        }
        event.cancellation().cancel();
        Set<BlockLocation> scanned = new HashSet<>();
        int captured = 0;
        for (BlockLocation start : event.block().neighbors()) {
            if (scanned.contains(start)) {
                continue;
            }
            CageScanner.Result cell = CageScanner.scan(context.platform().blocks(), start, settings.get());
            if (!cell.isCell()) {
                continue;
            }
            scanned.addAll(cell.interior());
            for (TargetState target : occupants(cell.interior(), event.actor())) {
                if (tryCapture(event.actor(), target, BlockLocation.of(target.location()), cell.interior(), item.get())) {
                    captured++;
                }
            }
        }
        if (captured == 0 && scanned.isEmpty()) {
            context.platform().feedback().actionBar(event.actor(), context.messages().text("captivity.not_a_cell"));
        }
    }

    private Optional<String> captureItem(TargetId actor, Optional<String> itemId, boolean cancelled) {
        if (cancelled || itemId.isEmpty() || !settings.get().captureItems().contains(itemId.get())
                || registry.isCaptive(actor)) {
            return Optional.empty();
        }
        return itemId;
    }

    private Set<TargetState> occupants(Set<BlockLocation> interior, TargetId actor) {
        BlockLocation first = interior.iterator().next();
        int minX = first.x(), minY = first.y(), minZ = first.z(), maxX = minX, maxY = minY, maxZ = minZ;
        for (BlockLocation block : interior) {
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }
        Set<TargetState> inside = new java.util.LinkedHashSet<>();
        for (TargetState target : context.platform().targets().livingIn(first.worldId(),
                new BlockLocation(first.worldId(), minX, minY, minZ),
                new BlockLocation(first.worldId(), maxX, maxY, maxZ))) {
            if (!target.id().equals(actor) && interior.contains(BlockLocation.of(target.location()))) {
                inside.add(target);
            }
        }
        return inside;
    }

    private boolean tryCapture(TargetId actor, TargetState target, BlockLocation anchor,
                               Set<BlockLocation> interior, String item) {
        Optional<CaptureFailure> failure = registry.check(Optional.of(actor), target, false);
        if (failure.isPresent()) {
            context.platform().feedback().actionBar(actor,
                    context.messages().text("captivity.failure." + failure.get().name()));
            return false;
        }
        registry.add(target, Optional.of(actor), Optional.of(anchor), CaptureMethod.CAGE);
        watcher.remember(target.id(), interior);
        if (settings.get().consumeItem()) {
            context.platform().items().consume(actor, item, 1);
        }
        return true;
    }
}
