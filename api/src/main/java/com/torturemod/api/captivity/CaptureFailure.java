package com.torturemod.api.captivity;

public enum CaptureFailure {
    NOT_FOUND,
    SELF,
    ALREADY_CAPTIVE,
    IMMUNE,
    BLACKLISTED,
    RECENTLY_RELEASED,
    REGION,
    /** Нет права, например в чужой тюрьме. */
    NOT_ALLOWED
}
