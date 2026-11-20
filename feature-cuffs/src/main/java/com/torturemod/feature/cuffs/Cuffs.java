package com.torturemod.feature.cuffs;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.help.Hints;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.util.Json;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Кто в наручниках и кто их надел. Состояние в памяти, запись в базу в фоне. */
final class Cuffs implements CuffsService {
    private static final String COLLECTION = "cuffs";

    private final FeatureContext context;
    private final CaptivityService captivity;
    /** Пленный → кто надел наручники; пусто, если надели сами собой при захвате без палача. */
    private final Map<TargetId, Optional<TargetId>> cuffed = new HashMap<>();

    Cuffs(FeatureContext context, CaptivityService captivity) {
        this.context = context;
        this.captivity = captivity;
    }

    void load() {
        context.storage().loadDocuments(COLLECTION).join().forEach((key, json) -> {
            TargetId captive = new TargetId(UUID.fromString(key));
            if (!captivity.isCaptive(captive)) {
                context.storage().deleteDocument(COLLECTION, key);
                return;
            }
            Object by = Json.readObject(json).get("by");
            cuffed.put(captive, by == null ? Optional.empty() : Optional.of(new TargetId(UUID.fromString(by.toString()))));
        });
    }

    @Override
    public boolean isCuffed(TargetId captive) {
        return cuffed.containsKey(captive);
    }

    Optional<TargetId> cuffedBy(TargetId captive) {
        return cuffed.getOrDefault(captive, Optional.empty());
    }

    @Override
    public void cuff(TargetId captive) {
        cuff(captive, Optional.empty());
    }

    void cuff(TargetId captive, Optional<TargetId> by) {
        if (!captivity.isCaptive(captive)) {
            return;
        }
        cuffed.put(captive, by);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("by", by.map(id -> id.value().toString()).orElse(null));
        context.storage().saveDocument(COLLECTION, captive.value().toString(), Json.write(document))
                .exceptionally(this::logFailure);
        context.platform().feedback().message(captive, context.messages().text("cuffs.you_are_cuffed"));
        context.services().find(Hints.class).ifPresent(hints -> hints.hint(captive, "hint.cuffs.captive"));
    }

    @Override
    public boolean uncuff(TargetId captive) {
        if (cuffed.remove(captive) == null) {
            return false;
        }
        context.storage().deleteDocument(COLLECTION, captive.value().toString()).exceptionally(this::logFailure);
        context.platform().feedback().message(captive, context.messages().text("cuffs.you_are_uncuffed"));
        context.services().find(Hints.class).ifPresent(hints -> hints.hint(captive, "hint.cuffs.free_hands"));
        return true;
    }

    void forget(TargetId captive) {
        if (cuffed.remove(captive) != null) {
            context.storage().deleteDocument(COLLECTION, captive.value().toString()).exceptionally(this::logFailure);
        }
    }

    private Void logFailure(Throwable error) {
        context.platform().log().error("Cuffs storage failed", error);
        return null;
    }
}
