package com.torturemod.api.feature;

import java.util.Optional;

/** Фичи не зависят друг от друга напрямую: одна предоставляет интерфейс из api, другая находит его здесь. */
public interface ServiceRegistry {
    <T> void provide(Class<T> type, T service);

    <T> Optional<T> find(Class<T> type);
}
