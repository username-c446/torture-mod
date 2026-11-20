package com.torturemod.testkit;

import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Хранилище в памяти: операции завершаются сразу. */
public final class InMemoryStorage implements Storage {
    public final Map<TargetId, CaptivityRecord> records = new LinkedHashMap<>();
    /** Коллекция → ключ → JSON. */
    public final Map<String, Map<String, String>> documents = new LinkedHashMap<>();

    @Override
    public CompletableFuture<String> open() {
        return CompletableFuture.completedFuture("memory");
    }

    @Override
    public CompletableFuture<Optional<CaptivityRecord>> load(TargetId target) {
        return CompletableFuture.completedFuture(Optional.ofNullable(records.get(target)));
    }

    @Override
    public CompletableFuture<List<CaptivityRecord>> loadAll() {
        return CompletableFuture.completedFuture(List.copyOf(records.values()));
    }

    @Override
    public CompletableFuture<Void> save(CaptivityRecord record) {
        records.put(record.target(), record);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(TargetId target) {
        records.remove(target);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, String>> loadDocuments(String collection) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(documents.getOrDefault(collection, Map.of())));
    }

    @Override
    public CompletableFuture<Void> saveDocument(String collection, String key, String json) {
        documents.computeIfAbsent(collection, ignored -> new LinkedHashMap<>()).put(key, json);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteDocument(String collection, String key) {
        Map<String, String> map = documents.get(collection);
        if (map != null) {
            map.remove(key);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
    }
}
