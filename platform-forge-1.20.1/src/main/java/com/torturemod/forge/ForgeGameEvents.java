package com.torturemod.forge;

import com.torturemod.api.event.GameEvent;
import com.torturemod.api.port.GameEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class ForgeGameEvents implements GameEvents {
    private final List<Consumer<GameEvent>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void listen(Consumer<GameEvent> listener) {
        listeners.add(listener);
    }

    void fire(GameEvent event) {
        listeners.forEach(listener -> listener.accept(event));
    }
}
