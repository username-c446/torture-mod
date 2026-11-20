package com.torturemod.api.prison;

/** Назначение сундука в тюрьме. */
public enum ChestRole {
    /** Пленные сдают сюда норму. */
    DEPOSIT,
    /** Отсюда берут награды за норму. */
    REWARDS,
    /** Через него выкупают пленных. */
    RANSOM,
    /** Сюда курьеры относят сданное. */
    WAREHOUSE
}
