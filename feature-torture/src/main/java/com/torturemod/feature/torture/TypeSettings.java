package com.torturemod.feature.torture;

import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.items.TortureTypes;

import java.util.Map;

/** Настройки одного типа пытки. Значения по умолчанию совпадают с config.yml. */
record TypeSettings(
        String id,
        long cooldownMillis,
        double resistanceDrop,
        double healthDamage,
        int fireTicks,
        int airTicks,
        int poisonTicks,
        int hungerPoints,
        String sound,
        String particle,
        String icon) {

    private record Defaults(int cooldown, double drop, String sound, String particle, String icon) {
    }

    private static final Map<String, Defaults> DEFAULTS = Map.of(
            TortureTypes.STRIKE, new Defaults(3, 0.25, "minecraft:entity.player.attack.strong",
                    "minecraft:damage_indicator", "minecraft:iron_sword"),
            TortureTypes.FIRE, new Defaults(10, 0.4, "minecraft:item.flintandsteel.use", "minecraft:flame",
                    "minecraft:flint_and_steel"),
            TortureTypes.DROWN, new Defaults(15, 0.5, "minecraft:entity.player.splash", "minecraft:splash",
                    "minecraft:water_bucket"),
            TortureTypes.POISON, new Defaults(15, 0.5, "minecraft:entity.witch.drink", "minecraft:witch",
                    "minecraft:spider_eye"));

    static TypeSettings from(ConfigView config, String id) {
        Defaults defaults = DEFAULTS.getOrDefault(id, new Defaults(10, 0.3, "", "", "minecraft:barrier"));
        String base = "torture.types." + id + ".";
        return new TypeSettings(
                id,
                Math.max(0, config.getDouble(base + "cooldown_seconds", defaults.cooldown())) > 0
                        ? (long) (config.getDouble(base + "cooldown_seconds", defaults.cooldown()) * 1000) : 0,
                Math.max(0, config.getDouble(base + "resistance_drop", defaults.drop())),
                Math.max(0, config.getDouble(base + "health_damage", 1.0)),
                Math.max(0, config.getInt(base + "fire_ticks", 60)),
                Math.max(0, config.getInt(base + "air_ticks", 150)),
                Math.max(0, config.getInt(base + "poison_ticks", 100)),
                Math.max(0, config.getInt(base + "hunger_points", 2)),
                config.getString(base + "sound", defaults.sound()),
                config.getString(base + "particle", defaults.particle()),
                config.getString(base + "icon", defaults.icon()));
    }
}
