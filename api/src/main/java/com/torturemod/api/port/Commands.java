package com.torturemod.api.port;

import com.torturemod.api.model.TargetId;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface Commands {
    void register(String root, Handler handler);

    @FunctionalInterface
    interface Handler {
        void handle(Invocation invocation);
    }

    /** @param sender игрок; пусто для консоли */
    record Invocation(Optional<TargetId> sender, List<String> args, Consumer<String> reply) {
    }
}
