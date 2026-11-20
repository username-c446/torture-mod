package com.torturemod.feature.prison;

import com.torturemod.api.model.Area;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Зона тюрьмы: двор, шахта, ферма. Флаги и норма зоны, приоритет решает при пересечении зон. */
final class Zone {
    String name;
    final Area area;
    int priority;
    final Set<Flag> flags;
    /** ID предмета → сколько сдать за игровой день; пусто, если у зоны нет своей нормы. */
    final Map<String, Integer> quota = new LinkedHashMap<>();

    Zone(String name, Area area, int priority, Set<Flag> flags) {
        this.name = name;
        this.area = area;
        this.priority = priority;
        this.flags = flags.isEmpty() ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flags);
    }
}
