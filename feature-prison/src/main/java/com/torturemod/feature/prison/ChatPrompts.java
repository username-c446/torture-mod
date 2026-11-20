package com.torturemod.feature.prison;

import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.TargetId;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Ввод текста без команд: следующее сообщение игрока в чат становится ответом, в чат оно не уходит. */
final class ChatPrompts {
    private static final long TIMEOUT_MILLIS = 60_000;
    private static final int MAX_LENGTH = 32;

    private record Prompt(long expiresAt, Consumer<String> answer) {
    }

    private final Support s;
    private final Map<TargetId, Prompt> prompts = new HashMap<>();

    ChatPrompts(Support support) {
        this.s = support;
    }

    void ask(TargetId player, String questionKey, Consumer<String> answer) {
        s.context.platform().menus().close(player);
        prompts.put(player, new Prompt(s.context.clock().millis() + TIMEOUT_MILLIS, answer));
        s.message(player, questionKey);
        s.message(player, "prison.prompt.cancel_hint");
    }

    void onChat(GameEvent.ChatSent event) {
        Prompt prompt = prompts.remove(event.player());
        if (prompt == null || event.cancellation().isCancelled()) {
            return;
        }
        event.cancellation().cancel();
        String text = event.message().strip();
        if (s.context.clock().millis() > prompt.expiresAt()) {
            s.message(event.player(), "prison.prompt.expired");
            return;
        }
        if (text.isEmpty() || text.equalsIgnoreCase(s.text("prison.prompt.cancel_word"))) {
            s.message(event.player(), "prison.prompt.cancelled");
            return;
        }
        prompt.answer().accept(text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text);
    }
}
