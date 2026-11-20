package com.torturemod.feature.captivity;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.captivity.CaptiveAction;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/** Ограничения пленного игрока: блоки, предметы, атака, команды, чат. Двигаться внутри клетки можно. */
final class Restrictions {
    private final FeatureContext context;
    private final CaptivityRegistry registry;
    private final Supplier<CaptivitySettings> settings;

    Restrictions(FeatureContext context, CaptivityRegistry registry, Supplier<CaptivitySettings> settings) {
        this.context = context;
        this.registry = registry;
        this.settings = settings;
    }

    void blockAction(TargetId actor, CaptiveAction action, BlockLocation where, Cancellation cancellation) {
        if (!cancellation.isCancelled() && registry.isCaptivePlayer(actor) && !registry.mayAct(actor, action, where)) {
            cancellation.cancel();
            context.platform().feedback().actionBar(actor, context.messages().text("captivity.action_blocked"));
        }
    }

    /** Действие на месте пленного: предмет в воздухе, атака, выброс. */
    void blockAction(TargetId actor, CaptiveAction action, Cancellation cancellation) {
        if (cancellation.isCancelled() || !registry.isCaptivePlayer(actor)) {
            return;
        }
        Optional<TargetState> state = context.platform().targets().state(actor);
        if (state.isEmpty()) {
            cancellation.cancel();
            return;
        }
        blockAction(actor, action, BlockLocation.of(state.get().location()), cancellation);
    }

    void onChat(GameEvent.ChatSent event) {
        if (event.cancellation().isCancelled() || !registry.isCaptivePlayer(event.player())) {
            return;
        }
        event.cancellation().cancel();
        Optional<TargetState> speaker = context.platform().targets().state(event.player());
        if (speaker.isEmpty()) {
            return;
        }
        String text = context.messages().text("captivity.local_chat", speaker.get().name(), event.message());
        for (TargetState listener : context.platform().targets()
                .playersNear(speaker.get().location(), settings.get().localChatRadius())) {
            context.platform().feedback().message(listener.id(), text);
        }
    }

    void onCommand(GameEvent.CommandIssued event) {
        if (event.cancellation().isCancelled() || !registry.isCaptivePlayer(event.player())) {
            return;
        }
        if (!settings.get().allowedCommands().contains(root(event.command()))) {
            event.cancellation().cancel();
            context.platform().feedback().message(event.player(), context.messages().text("captivity.command_blocked"));
        }
    }

    /** Имя команды без косой черты и пространства имён: minecraft:tell → tell. */
    static String root(String command) {
        String trimmed = command.strip();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        String name = space < 0 ? trimmed : trimmed.substring(0, space);
        int colon = name.indexOf(':');
        return (colon < 0 ? name : name.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }
}
