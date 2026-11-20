package com.torturemod.api.captivity;

import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

import java.util.Optional;

/** Правило доступа к пленным, например права команды тюрьмы. Первое непустое решение побеждает. */
@FunctionalInterface
public interface AccessRule {
    /**
     * @param captive пленный; пусто, если цель ещё не в плену (захват)
     * @param where   где находится цель
     */
    Optional<Boolean> decide(TargetId actor, AccessRight right, Optional<Captive> captive, Location where);
}
