package com.torturemod.api.prison;

import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

/** Пленный вышел за границы тюрьмы. @param seen видел ли его кто-то из охраны */
public record PrisonEscaped(String prisonId, TargetId captive, Location where, boolean seen) {
}
