package com.torturemod.api.items;

import java.util.List;
import java.util.Map;

/**
 * Рецепт верстака по форме. @param pattern строки сетки, символ — ключ из keys, пробел — пусто
 * @param keys символ → ID предмета
 */
public record ShapedRecipe(String id, List<String> pattern, Map<Character, String> keys, String resultId, int count) {
}
