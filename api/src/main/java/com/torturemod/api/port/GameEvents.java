package com.torturemod.api.port;

import com.torturemod.api.event.GameEvent;

import java.util.function.Consumer;

/** Входящие события платформы. */
public interface GameEvents {
    void listen(Consumer<GameEvent> listener);
}
