package com.torturemod.api.feature;

import com.torturemod.api.port.Commands;

/** Подкоманды /torturemod. Обработчик получает аргументы после имени подкоманды. */
public interface CommandRegistry {
    /** @param permission право, нужное игроку; консоли доступно всё */
    void register(String name, String permission, Commands.Handler handler);
}
