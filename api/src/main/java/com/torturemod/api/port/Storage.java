package com.torturemod.api.port;

import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.TargetId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Все операции выполняются вне основного потока сервера. */
public interface Storage extends AutoCloseable {
    /** Открывает хранилище и возвращает его описание для лога. */
    CompletableFuture<String> open();

    CompletableFuture<Optional<CaptivityRecord>> load(TargetId target);

    CompletableFuture<List<CaptivityRecord>> loadAll();

    CompletableFuture<Void> save(CaptivityRecord record);

    CompletableFuture<Void> delete(TargetId target);

    /** Документы фич: ключ → JSON. Коллекция называется по фиче, например prisons. */
    CompletableFuture<Map<String, String>> loadDocuments(String collection);

    CompletableFuture<Void> saveDocument(String collection, String key, String json);

    CompletableFuture<Void> deleteDocument(String collection, String key);

    /** Дожидается завершения начатых операций. */
    @Override
    void close();
}
