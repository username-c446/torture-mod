package com.torturemod.feature.prison;

import com.torturemod.api.captivity.CaptiveAction;

/** Флаги зоны: что пленному без наручников можно делать руками. */
enum Flag {
    BREAK,
    PLACE,
    USE,
    EAT,
    ATTACK,
    DROP;

    static Flag of(CaptiveAction action) {
        return switch (action) {
            case BREAK -> BREAK;
            case PLACE -> PLACE;
            case USE_BLOCK, USE_ENTITY, USE_ITEM -> USE;
            case EAT -> EAT;
            case DROP -> DROP;
            case ATTACK -> ATTACK;
        };
    }
}
