package com.torturemod.feature.help;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.help.Hints;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.util.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Подсказки по месту: игрок видит каждую один раз, отметки хранятся в базе. */
final class HintBook implements Hints {
    private static final String COLLECTION = "hints";

    private final FeatureContext context;
    private final Map<TargetId, Set<String>> shown = new HashMap<>();

    HintBook(FeatureContext context) {
        this.context = context;
    }

    /** Однократная загрузка при запуске. */
    void load() {
        context.storage().loadDocuments(COLLECTION).join().forEach((key, json) -> {
            Set<String> keys = new HashSet<>();
            if (Json.read(json) instanceof List<?> list) {
                list.forEach(item -> keys.add(String.valueOf(item)));
            }
            shown.put(new TargetId(UUID.fromString(key)), keys);
        });
    }

    @Override
    public void hint(TargetId player, String key, Object... args) {
        if (!context.config().getBoolean("help.hints", true)) {
            return;
        }
        Set<String> keys = shown.computeIfAbsent(player, ignored -> new HashSet<>());
        if (!keys.add(key)) {
            return;
        }
        String text = context.messages().text(key, args);
        context.platform().feedback().message(player, text);
        context.platform().feedback().actionBar(player, text);
        context.storage().saveDocument(COLLECTION, player.value().toString(), Json.write(new ArrayList<>(keys)))
                .exceptionally(error -> {
                    context.platform().log().error("Cannot save hints", error);
                    return null;
                });
    }
}
