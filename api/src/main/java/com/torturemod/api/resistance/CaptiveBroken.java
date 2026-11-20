package com.torturemod.api.resistance;

import com.torturemod.api.model.TargetId;

/** Публикуется на шине, когда сопротивление пленного дошло до нуля. */
public record CaptiveBroken(TargetId captive) {
}
