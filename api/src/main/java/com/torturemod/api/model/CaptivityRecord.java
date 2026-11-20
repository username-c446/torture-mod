package com.torturemod.api.model;

import java.util.Optional;

/**
 * Сохраняемое состояние плена. Хранится отдельно от сущности, поэтому доступно и офлайн.
 *
 * @param location место захвата
 * @param cell     опорный блок клетки; пусто, если пленный взят без клетки
 */
public record CaptivityRecord(
        TargetId target,
        TargetKind kind,
        Optional<TargetId> captor,
        double resistance,
        boolean broken,
        long capturedAtEpochMillis,
        Location location,
        Optional<BlockLocation> cell) {
}
