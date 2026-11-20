package com.torturemod.feature.prison;

import java.util.LinkedHashMap;
import java.util.Map;

/** Условия содержания пленного в тюрьме: срок, цена выкупа, норма и её выполнение за игровой день. */
final class Inmate {
    String prisonId;
    /** Сколько осталось сидеть, пока пленный онлайн; -1, если срока нет. */
    long sentenceLeftMillis = -1;
    /** Своя норма пленного; пусто, если действует норма зоны или тюрьмы. */
    final Map<String, Integer> quotaOverride = new LinkedHashMap<>();
    final Map<String, Integer> progress = new LinkedHashMap<>();
    long dayElapsedMillis;
    boolean completedToday;
    /** ID предмета → количество; пусто, если выкупить нельзя. */
    final Map<String, Integer> price = new LinkedHashMap<>();

    Inmate(String prisonId) {
        this.prisonId = prisonId;
    }

    boolean hasSentence() {
        return sentenceLeftMillis >= 0;
    }
}
