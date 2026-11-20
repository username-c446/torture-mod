package com.torturemod.api.prison;

import com.torturemod.api.model.TargetId;

/** Мобы-охранники тюрьмы. Предоставляет фича слуг. */
public interface PrisonGuards {
    boolean anyGuardSees(String prisonId, TargetId target, double maxDistance);
}
