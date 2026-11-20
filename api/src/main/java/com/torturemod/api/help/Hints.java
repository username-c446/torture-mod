package com.torturemod.api.help;

import com.torturemod.api.model.TargetId;

/** Подсказки по месту: каждая показывается игроку один раз над хотбаром. */
public interface Hints {
    /** @param key ключ текста подсказки в языковом файле */
    void hint(TargetId player, String key, Object... args);
}
