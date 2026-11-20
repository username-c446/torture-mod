package com.torturemod.feature.prison;

import com.torturemod.api.feature.ConfigView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Снимок настроек тюрьмы. Значения по умолчанию совпадают с config.yml. */
record PrisonSettings(
        String markerItem,
        Set<String> consoleBlocks,
        Set<String> chestBlocks,
        int maxPrisonsPerPlayer,
        int minSide,
        int maxSide,
        int maxZones,
        double alarmDistance,
        long dayMillis,
        int quotaMaxKinds,
        int quotaMaxCount,
        long dealExpiryMillis,
        long rewardRecoveryMillis,
        int maxSentenceMinutes,
        Map<String, String> ownershipRoles) {

    static PrisonSettings from(ConfigView config) {
        Map<String, String> roles = new HashMap<>();
        List<String> pairs = config.has("prison.ownership_roles")
                ? config.getStringList("prison.ownership_roles") : List.of("mayor=owner", "assistant=coowner");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                roles.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return new PrisonSettings(
                config.getString("prison.marker_item", "minecraft:stick"),
                set(config, "prison.console_blocks", List.of("minecraft:lectern")),
                set(config, "prison.chest_blocks", List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")),
                Math.max(1, config.getInt("prison.max_prisons_per_player", 3)),
                Math.max(1, config.getInt("prison.min_side", 3)),
                Math.max(1, config.getInt("prison.max_side", 128)),
                Math.max(0, config.getInt("prison.max_zones", 16)),
                config.getDouble("prison.alarm_distance", 24),
                Math.max(1, config.getInt("prison.day_minutes", 20)) * 60_000L,
                Math.max(1, config.getInt("prison.quota.max_kinds", 9)),
                Math.max(1, config.getInt("prison.quota.max_count", 1024)),
                (long) (Math.max(0.01, config.getDouble("prison.deal_expiry_hours", 24)) * 3_600_000L),
                Math.max(0, config.getInt("prison.reward_recovery_minutes", 20)) * 60_000L,
                Math.max(1, config.getInt("prison.max_sentence_minutes", 10080)),
                Map.copyOf(roles));
    }

    private static Set<String> set(ConfigView config, String path, List<String> fallback) {
        return Set.copyOf(config.has(path) ? config.getStringList(path) : fallback);
    }
}
