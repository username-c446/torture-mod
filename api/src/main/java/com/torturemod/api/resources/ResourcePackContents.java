package com.torturemod.api.resources;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Ресурспак сервера: фичи кладут в него текстуры и модели при запуске, мод собирает zip.
 * Интеграции с плагинами вроде ForcePack берут готовый файл через {@link #built()}.
 */
public interface ResourcePackContents {
    /** @param path путь внутри пака, например assets/torturemod/textures/item/handcuffs.png */
    void add(String path, byte[] content);

    /** Своя модель для ванильного предмета с этим номером CustomModelData. */
    void overrideItemModel(String baseItemId, int customModelData, String model);

    /** Собранный пак; пусто, пока он собирается. */
    Optional<BuiltPack> built();

    record BuiltPack(Path file, String sha1) {
    }
}
