package com.torturemod.api.port;

import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Мобы на службе у игроков. Платформа даёт простые действия, решения принимает фича:
 * куда идти, кого атаковать, что нести.
 */
public interface Minions {
    /**
     * Отключает мобу собственный выбор целей и исчезновение. Вызов повторяют, когда моб снова загрузился:
     * для уже подчинённого он ничего не делает. @return false, если моба нет в мире
     */
    boolean enslave(TargetId mob);

    /** Возвращает мобу обычное поведение. */
    void free(TargetId mob);

    void moveTo(TargetId mob, Location to, double speed);

    /** Цель атаки; пусто снимает её. Мобы без атаки цель не преследуют. */
    void setAttackTarget(TargetId mob, Optional<TargetId> target);

    /** Подпись над мобом; пустая строка убирает её. */
    void setLabel(TargetId mob, String label);

    /** Предмет в основной руке, который видят игроки; пусто убирает его. */
    void setHeld(TargetId mob, Optional<ItemStackData> stack);

    /** Броня из руки игрока уходит в свой слот, остальное в руку моба. Прежнее снаряжение получает игрок. */
    boolean equipFromHand(TargetId player, TargetId mob);

    /** Слот снаряжения → ID предмета, только занятые слоты. */
    Map<String, String> equipment(TargetId mob);

    /** Собственный инвентарь моба: у жителя это собранный урожай. */
    List<ItemStackData> inventory(TargetId mob);

    /** Забирает предметы из инвентаря моба. @return сколько забрано */
    int removeFromInventory(TargetId mob, String itemId, int count);

    /** Ванильная репутация жителя: торговля с игроком становится дешевле. */
    void befriend(TargetId villager, TargetId player);

    /** Роняет вещи на месте моба: например, то, что нёс погибший курьер. */
    void drop(TargetId mob, List<ItemStackData> stacks);

    /** Профессия жителя, например minecraft:farmer. */
    Optional<String> profession(TargetId villager);
}
