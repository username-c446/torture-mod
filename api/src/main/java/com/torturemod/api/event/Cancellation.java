package com.torturemod.api.event;

/** Позволяет фиче запретить действие игрока. */
public final class Cancellation {
    private boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
