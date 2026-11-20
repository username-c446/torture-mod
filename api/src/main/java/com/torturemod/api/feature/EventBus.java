package com.torturemod.api.feature;

import java.util.function.Consumer;

/** Синхронная шина на основном потоке. Подписка идёт на точный класс события. */
public interface EventBus {
    <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener);

    void publish(Object event);

    interface Subscription {
        void cancel();
    }
}
