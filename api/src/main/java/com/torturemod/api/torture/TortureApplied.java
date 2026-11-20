package com.torturemod.api.torture;

import com.torturemod.api.model.TargetId;

/** Публикуется на шине после каждого действия пытки. */
public record TortureApplied(TargetId captive, TargetId actor, String type, double resistanceDrop) {
}
