package com.torturemod.api.torture;

import java.util.List;

/** Кнопка, которую другая фича добавляет в меню пленного. */
public record MenuButton(String itemId, String label, List<String> lore, Runnable onClick) {
}
