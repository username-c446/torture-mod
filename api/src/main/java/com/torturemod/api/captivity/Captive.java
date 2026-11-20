package com.torturemod.api.captivity;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;

import java.util.Optional;

/**
 * @param cell       опорный блок клетки; пусто, если пленный взят без клетки
 * @param resistance последнее сохранённое сопротивление; живое значение ведёт фича сопротивления
 */
public record Captive(TargetId target, TargetKind kind, Optional<TargetId> captor, Optional<BlockLocation> cell,
                      long capturedAtEpochMillis, double resistance, boolean broken) {
}
