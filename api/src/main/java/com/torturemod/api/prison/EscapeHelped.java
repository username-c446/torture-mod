package com.torturemod.api.prison;

import com.torturemod.api.model.TargetId;

/** Чужой помогает пленному тюрьмы: режет наручники, освобождает или нападает на охрану. */
public record EscapeHelped(String prisonId, TargetId helper, TargetId captive) {
}
