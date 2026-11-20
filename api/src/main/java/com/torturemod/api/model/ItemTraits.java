package com.torturemod.api.model;

import java.util.Set;

/**
 * Признаки предмета для автоопределения типа пытки.
 *
 * @param kinds категории, выведенные платформой из класса предмета (например, sword). На платформе
 *              без модовых классов набор выводится из материала, поэтому может быть неполным.
 */
public record ItemTraits(String itemId, String namespace, Set<String> tags, Set<String> kinds, double attackDamage) {
}
