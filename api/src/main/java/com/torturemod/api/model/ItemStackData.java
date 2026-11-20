package com.torturemod.api.model;

/**
 * Стопка предметов, которую ядро может хранить и передавать дальше без потерь.
 *
 * @param data данные стопки в формате платформы (зачарования, имя, прочность); пусто, если их нет
 */
public record ItemStackData(String itemId, int count, String data) {
    public ItemStackData {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        data = data == null ? "" : data;
    }

    public static ItemStackData of(String itemId, int count) {
        return new ItemStackData(itemId, count, "");
    }

    public ItemStackData withCount(int newCount) {
        return new ItemStackData(itemId, newCount, data);
    }
}
