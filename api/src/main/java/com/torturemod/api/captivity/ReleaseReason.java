package com.torturemod.api.captivity;

public enum ReleaseReason {
    ADMIN,
    /** Отпустил палач. */
    FREED,
    DEATH,
    CELL_BROKEN,
    LEFT_CELL,
    ESCAPE,
    RANSOM,
    SENTENCE_SERVED,
    EXECUTED,
    /** Сломленный моб поступил на службу тюрьме. */
    SERVICE
}
