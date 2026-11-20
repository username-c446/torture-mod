package com.torturemod.feature.resistance;

import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.model.TargetKind;

/** Снимок настроек шкалы. Профили мобов читаются из того же снимка по ID моба. */
record ResistanceSettings(
        double playerMax,
        long recoveryDelayMillis,
        double recoveryMinutes,
        double habituationDecay,
        double habituationMinimum,
        double habituationRecoveryPerSecond,
        ConfigView config) {

    static ResistanceSettings from(ConfigView config) {
        return new ResistanceSettings(
                Math.max(1, config.getDouble("resistance.max", 100)),
                Math.max(0, config.getInt("resistance.recovery.delay_seconds", 60)) * 1000L,
                Math.max(1, config.getDouble("resistance.recovery.full_recovery_minutes", 60)),
                clamp(config.getDouble("resistance.habituation.decay", 0.85)),
                clamp(config.getDouble("resistance.habituation.minimum", 0.2)),
                Math.max(0, config.getDouble("resistance.habituation.recovery_per_second", 0.01)),
                config);
    }

    double maxFor(TargetKind kind, String typeId) {
        if (kind == TargetKind.PLAYER) {
            return playerMax;
        }
        return Math.max(1, config.getDouble(profile(typeId) + ".max", playerMax));
    }

    double vulnerability(String typeId, String tortureType) {
        return typeId == null ? 1 : Math.max(0, config.getDouble(profile(typeId) + ".vulnerability." + tortureType, 1));
    }

    double recoveryPerSecond(double max) {
        return max / (recoveryMinutes * 60);
    }

    private static String profile(String typeId) {
        return "resistance.mobs." + typeId;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
