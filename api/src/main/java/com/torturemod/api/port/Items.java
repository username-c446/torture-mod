package com.torturemod.api.port;

import com.torturemod.api.items.ShapedRecipe;
import com.torturemod.api.items.CustomItem;
import com.torturemod.api.model.ItemTraits;
import com.torturemod.api.model.TargetId;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Предметы игрока. Слоты как в инвентаре игрока: 0–8 панель, 9–35 рюкзак, 40 вторая рука.
 */
public interface Items {
    int OFFHAND_SLOT = 40;

    Optional<ItemTraits> traits(String itemId);

    /** Все известные предметы: нужны для файла-отчёта по автоопределению. */
    Iterable<String> allItemIds();

    Optional<String> heldItem(TargetId actor);

    /** Слот руки, в которой лежит предмет: сначала основная, затем вторая. */
    OptionalInt findInHands(TargetId actor, String itemId);

    /** Забирает предметы из рук игрока. В творческом режиме ничего не забирает. */
    void consume(TargetId actor, String itemId, int count);

    /**
     * Расходует предмет в слоте, как его расходовала бы игра: инструмент теряет прочность,
     * ведро пустеет, зелье становится бутылкой, остальное уменьшается на один.
     */
    void spend(TargetId actor, int slot);

    boolean isFood(String itemId);

    /** Объявляет предмет мода: дальше он виден под своим ID в событиях, инвентарях и стопках. */
    void defineCustomItem(CustomItem item);

    /** Рецепт верстака, который игра показывает и клиенту без мода. */
    void addRecipe(ShapedRecipe recipe);

    /** Выдаёт ванильную карту с картинкой 128×128: цвета подбираются из палитры карт. */
    void giveMap(TargetId player, String name, int[] argb128);
}
