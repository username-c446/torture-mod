package com.torturemod.api.port;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;

import java.util.List;
import java.util.Optional;

/** Блоки-хранилища в мире: сундуки, бочки. Двойной сундук считается одним хранилищем. */
public interface Containers {
    /** Пусто, если здесь нет хранилища или чанк не загружен. */
    Optional<List<ItemStackData>> contents(BlockLocation block);

    /** Кладёт стопки, как воронка. @return что не поместилось */
    List<ItemStackData> insert(BlockLocation block, List<ItemStackData> stacks);

    /** Забирает до count предметов с таким ID. @return что удалось забрать */
    List<ItemStackData> remove(BlockLocation block, String itemId, int count);

    /** Забирает до maxStacks стопок по порядку слотов. */
    List<ItemStackData> takeStacks(BlockLocation block, int maxStacks);
}
