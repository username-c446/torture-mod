package com.torturemod.api.captivity;

/** Действия пленного руками. Без правил плена все они запрещены. */
public enum CaptiveAction {
    BREAK,
    PLACE,
    /** ПКМ по блоку: дверь, сундук, рычаг. */
    USE_BLOCK,
    USE_ENTITY,
    /** Предмет в воздухе, кроме еды. */
    USE_ITEM,
    EAT,
    DROP,
    ATTACK
}
