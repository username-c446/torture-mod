package com.torturemod.feature.interrogation;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Кто с кем часто бывает рядом. Раз в несколько секунд отмечает пары игроков поблизости; время в плену
 * не считается, иначе союзниками пленного стали бы его палачи. Данные живут в памяти до перезапуска.
 */
final class AllyTracker {
    private static final class Companion {
        int samples;
        long lastSeenMillis;
    }

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final Map<TargetId, Map<TargetId, Companion>> companions = new HashMap<>();
    private long ticks;

    AllyTracker(FeatureContext context, CaptivityService captivity) {
        this.context = context;
        this.captivity = captivity;
    }

    void tick() {
        ticks++;
        int interval = Math.max(1, setting("sample_seconds", 30)) * 20;
        if (ticks % interval != 0) {
            return;
        }
        long now = context.clock().millis();
        double radius = context.config().getDouble("interrogation.allies.radius", 32);
        for (TargetState player : context.platform().players().online()) {
            if (captivity.isCaptive(player.id())) {
                continue;
            }
            for (TargetState other : context.platform().targets().playersNear(player.location(), radius)) {
                if (!other.id().equals(player.id()) && !captivity.isCaptive(other.id())) {
                    Companion companion = companions.computeIfAbsent(player.id(), key -> new HashMap<>())
                            .computeIfAbsent(other.id(), key -> new Companion());
                    companion.samples++;
                    companion.lastSeenMillis = now;
                }
            }
        }
        long window = windowMillis();
        companions.values().forEach(map -> map.values().removeIf(companion -> now - companion.lastSeenMillis > window));
        companions.values().removeIf(Map::isEmpty);
    }

    List<TargetId> frequentCompanions(TargetId player) {
        long now = context.clock().millis();
        int minimum = Math.max(1, setting("min_samples", 10));
        return companions.getOrDefault(player, Map.of()).entrySet().stream()
                .filter(entry -> entry.getValue().samples >= minimum)
                .filter(entry -> now - entry.getValue().lastSeenMillis <= windowMillis())
                .sorted(Comparator.comparingInt((Map.Entry<TargetId, Companion> entry) -> entry.getValue().samples).reversed())
                .limit(Math.max(1, setting("max_listed", 5)))
                .map(Map.Entry::getKey)
                .toList();
    }

    private long windowMillis() {
        return (long) (context.config().getDouble("interrogation.allies.window_hours", 3) * 3_600_000);
    }

    private int setting(String key, int fallback) {
        return context.config().getInt("interrogation.allies." + key, fallback);
    }
}
