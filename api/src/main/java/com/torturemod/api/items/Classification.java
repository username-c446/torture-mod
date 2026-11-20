package com.torturemod.api.items;

import java.util.Set;

/**
 * Какие пытки даёт предмет.
 *
 * @param types    типы пыток; пусто, если предмет не участвует
 * @param strength множитель силы
 * @param reason   почему так решено: правило конфига или признак автоопределения
 */
public record Classification(String itemId, Set<String> types, double strength, String reason) {
}
