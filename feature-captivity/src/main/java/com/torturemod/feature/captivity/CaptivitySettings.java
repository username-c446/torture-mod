package com.torturemod.feature.captivity;

import com.torturemod.api.feature.ConfigView;

import java.util.List;
import java.util.Set;

/** Снимок настроек плена. Значения по умолчанию совпадают с config.yml. */
record CaptivitySettings(
        Set<String> captureItems,
        boolean consumeItem,
        Set<String> walls,
        Set<String> markers,
        int maxCellVolume,
        int cellCheckTicks,
        int positionCheckTicks,
        long recaptureImmunityMillis,
        Set<String> blacklist,
        double localChatRadius,
        Set<String> allowedCommands) {

    private static final List<String> DEFAULT_WALLS = List.of(
            "minecraft:iron_bars", "minecraft:iron_door", "minecraft:iron_trapdoor",
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
            "minecraft:chiseled_stone_bricks", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
            "minecraft:obsidian");
    private static final List<String> DEFAULT_BLACKLIST = List.of(
            "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian");

    static CaptivitySettings from(ConfigView config) {
        return new CaptivitySettings(
                set(config, "captivity.capture.items", List.of("minecraft:lead")),
                config.getBoolean("captivity.capture.consume_item", true),
                set(config, "captivity.cell.walls", DEFAULT_WALLS),
                set(config, "captivity.cell.markers", List.of("minecraft:lodestone")),
                Math.max(1, config.getInt("captivity.cell.max_volume", 512)),
                Math.max(1, config.getInt("captivity.cell.check_interval_ticks", 100)),
                Math.max(1, config.getInt("captivity.position_check_interval_ticks", 20)),
                Math.max(0, config.getInt("captivity.recapture_immunity_seconds", 300)) * 1000L,
                set(config, "captivity.blacklist", DEFAULT_BLACKLIST),
                config.getDouble("captivity.restrictions.local_chat_radius", 16),
                set(config, "captivity.restrictions.allowed_commands", List.of()));
    }

    private static Set<String> set(ConfigView config, String path, List<String> fallback) {
        return Set.copyOf(config.has(path) ? config.getStringList(path) : fallback);
    }
}
