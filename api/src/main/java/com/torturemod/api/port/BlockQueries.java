package com.torturemod.api.port;

import com.torturemod.api.model.BlockLocation;

import java.util.Optional;

public interface BlockQueries {
    /** Пусто, если чанк не загружен: чтение не должно загружать чанки. */
    Optional<String> blockId(BlockLocation location);
}
