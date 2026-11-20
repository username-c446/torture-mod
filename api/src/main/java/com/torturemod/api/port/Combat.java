package com.torturemod.api.port;

import com.torturemod.api.model.DamageKind;
import com.torturemod.api.model.TargetId;

import java.util.Optional;

/**
 * Воздействие на цель. Методы возвращают false, если игра эффект не применила:
 * огнестойкий не горит, нежить не травится, водный моб не задыхается.
 */
public interface Combat {
    boolean damage(TargetId target, DamageKind kind, float amount, Optional<TargetId> source);

    boolean ignite(TargetId target, int ticks);

    boolean drainAir(TargetId target, int ticks);

    /** Только для игроков. */
    boolean drainFood(TargetId target, int points);

    boolean applyEffect(TargetId target, String effectId, int ticks, int amplifier);

    void kill(TargetId target);
}
