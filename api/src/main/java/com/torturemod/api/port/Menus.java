package com.torturemod.api.port;

import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;

import java.util.List;
import java.util.function.Consumer;

/** Серверное меню на основе сундука: мод на клиенте не нужен, предметы из меню забрать нельзя. */
public interface Menus {
    void open(TargetId viewer, Menu menu);

    void close(TargetId viewer);

    /**
     * Меню-хранилище: игрок кладёт и забирает вещи как в сундуке. При закрытии всё, что в нём лежит,
     * передаётся фиче, а меню исчезает. Если игрок вышел, вызов тоже происходит.
     */
    void openStorage(TargetId viewer, StorageMenu menu);

    record Menu(String title, int rows, List<Entry> entries) {
    }

    /** @param onClose вызывается на основном потоке с содержимым меню */
    record StorageMenu(String title, int rows, List<ItemStackData> contents, Consumer<List<ItemStackData>> onClose) {
    }

    /** @param onClick вызывается на основном потоке; null, если на слот нельзя нажать */
    record Entry(int slot, String itemId, String label, List<String> lore, Runnable onClick) {
    }
}
