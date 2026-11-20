package com.torturemod.api.captivity;

/** Публикуется на шине после освобождения. */
public record CaptiveReleased(Captive captive, ReleaseReason reason) {
}
