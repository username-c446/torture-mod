package com.torturemod.api.items;

import java.util.List;

/** Встроенные типы пыток первой версии. */
public final class TortureTypes {
    public static final String STRIKE = "strike";
    public static final String FIRE = "fire";
    public static final String DROWN = "drown";
    public static final String POISON = "poison";
    public static final List<String> ALL = List.of(STRIKE, FIRE, DROWN, POISON);

    private TortureTypes() {
    }
}
