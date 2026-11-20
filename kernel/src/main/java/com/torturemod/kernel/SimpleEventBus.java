package com.torturemod.kernel;

import com.torturemod.api.feature.EventBus;
import com.torturemod.api.port.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class SimpleEventBus implements EventBus {
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    private final Log log;

    SimpleEventBus(Log log) {
        this.log = log;
    }

    @Override
    public <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        Consumer<Object> typed = event -> listener.accept(eventType.cast(event));
        List<Consumer<Object>> list = listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>());
        list.add(typed);
        return () -> list.remove(typed);
    }

    @Override
    public void publish(Object event) {
        List<Consumer<Object>> list = listeners.get(event.getClass());
        if (list == null) {
            return;
        }
        for (Consumer<Object> listener : list) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // Ошибка одной фичи не должна ломать остальные и тик сервера.
                log.error("Event listener failed: " + event.getClass().getName(), e);
            }
        }
    }
}
