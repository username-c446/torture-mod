package com.torturemod.api.captivity;

import com.torturemod.api.model.BlockLocation;

import java.util.Optional;

/**
 * Правило о том, что пленному можно делать руками. Правила спрашиваются по приоритету, от меньшего,
 * и первое непустое решение побеждает. Если не решило ни одно, действие запрещено.
 */
@FunctionalInterface
public interface ActionRule {
    /** @return разрешить, запретить или пусто, если правило не решает */
    Optional<Boolean> decide(Captive captive, CaptiveAction action, BlockLocation where);
}
