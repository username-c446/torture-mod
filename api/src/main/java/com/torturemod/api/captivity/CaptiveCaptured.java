package com.torturemod.api.captivity;

/** Публикуется на шине после захвата. */
public record CaptiveCaptured(Captive captive, CaptureMethod method) {
}
