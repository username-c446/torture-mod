package com.torturemod.api.port;

import com.torturemod.api.model.TargetId;

/**
 * Тело игрока, который не в сети: стоит в мире вместо него, его можно пытать и убить.
 * Пока тело существует, остальные порты обращаются с ним как с самим игроком по его {@link TargetId}.
 * Реализация зависит от платформы и версии игры.
 */
public interface Bodies {
    /** Создаёт тело из живого состояния выходящего игрока или из его сохранения. @return false, если не вышло */
    boolean spawn(TargetId player);

    boolean exists(TargetId player);

    /** Игрок зашёл: переносит в него состояние тела (место, здоровье, эффекты, вещи) и убирает тело. */
    void restore(TargetId player);

    /** Убирает тело и сохраняет его состояние игроку, чтобы при заходе он оказался там же. */
    void remove(TargetId player);
}
