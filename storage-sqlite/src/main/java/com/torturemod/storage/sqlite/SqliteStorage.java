package com.torturemod.storage.sqlite;

import java.util.Map;
import java.util.LinkedHashMap;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.port.Storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Один фоновый поток и одно соединение: запросы выполняются строго по очереди. */
public final class SqliteStorage implements Storage {
    private static final int SCHEMA_VERSION = 3;
    private static final String COLUMNS =
            "target, kind, captor, resistance, broken, captured_at, world, x, y, z, cell_world, cell_x, cell_y, cell_z";

    private final Path file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "torturemod-storage");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public SqliteStorage(Path file) {
        this.file = file;
    }

    @Override
    public CompletableFuture<String> open() {
        return submit(() -> {
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
            } catch (IOException e) {
                throw new SQLException("Cannot create " + file.getParent(), e);
            }
            // Драйвер создаётся напрямую: DriverManager ненадёжен в модульных загрузчиках классов.
            connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), new Properties());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                migrate(statement);
                try (ResultSet version = statement.executeQuery("SELECT sqlite_version()")) {
                    version.next();
                    return "SQLite " + version.getString(1) + ", " + file.getFileName();
                }
            }
        });
    }

    private void migrate(Statement statement) throws SQLException {
        int version;
        try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            result.next();
            version = result.getInt(1);
        }
        if (version > SCHEMA_VERSION) {
            throw new SQLException("Database schema " + version + " is newer than supported " + SCHEMA_VERSION);
        }
        if (version < 1) {
            statement.execute("""
                    CREATE TABLE captivity (
                        target TEXT PRIMARY KEY,
                        kind TEXT NOT NULL,
                        captor TEXT,
                        resistance REAL NOT NULL,
                        broken INTEGER NOT NULL,
                        captured_at INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL
                    )""");
        }
        if (version < 2) {
            statement.execute("ALTER TABLE captivity ADD COLUMN cell_world TEXT");
            statement.execute("ALTER TABLE captivity ADD COLUMN cell_x INTEGER");
            statement.execute("ALTER TABLE captivity ADD COLUMN cell_y INTEGER");
            statement.execute("ALTER TABLE captivity ADD COLUMN cell_z INTEGER");
        }
        if (version < 3) {
            statement.execute("""
                    CREATE TABLE documents (
                        collection TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        PRIMARY KEY (collection, key)
                    )""");
        }
        if (version < SCHEMA_VERSION) {
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    @Override
    public CompletableFuture<Optional<CaptivityRecord>> load(TargetId target) {
        return submit(() -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM captivity WHERE target = ?")) {
                query.setString(1, target.value().toString());
                try (ResultSet row = query.executeQuery()) {
                    return row.next() ? Optional.of(read(row)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<CaptivityRecord>> loadAll() {
        return submit(() -> {
            try (PreparedStatement query = connection.prepareStatement("SELECT " + COLUMNS + " FROM captivity");
                 ResultSet row = query.executeQuery()) {
                List<CaptivityRecord> records = new ArrayList<>();
                while (row.next()) {
                    records.add(read(row));
                }
                return records;
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(CaptivityRecord record) {
        return submit(() -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "INSERT OR REPLACE INTO captivity (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                update.setString(1, record.target().value().toString());
                update.setString(2, record.kind().name());
                update.setString(3, record.captor().map(id -> id.value().toString()).orElse(null));
                update.setDouble(4, record.resistance());
                update.setInt(5, record.broken() ? 1 : 0);
                update.setLong(6, record.capturedAtEpochMillis());
                update.setString(7, record.location().worldId());
                update.setDouble(8, record.location().x());
                update.setDouble(9, record.location().y());
                update.setDouble(10, record.location().z());
                BlockLocation cell = record.cell().orElse(null);
                update.setString(11, cell == null ? null : cell.worldId());
                setNullableInt(update, 12, cell == null ? null : cell.x());
                setNullableInt(update, 13, cell == null ? null : cell.y());
                setNullableInt(update, 14, cell == null ? null : cell.z());
                update.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> delete(TargetId target) {
        return submit(() -> {
            try (PreparedStatement update = connection.prepareStatement("DELETE FROM captivity WHERE target = ?")) {
                update.setString(1, target.value().toString());
                update.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> loadDocuments(String collection) {
        return submit(() -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT key, value FROM documents WHERE collection = ?")) {
                query.setString(1, collection);
                try (ResultSet row = query.executeQuery()) {
                    Map<String, String> documents = new LinkedHashMap<>();
                    while (row.next()) {
                        documents.put(row.getString(1), row.getString(2));
                    }
                    return documents;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveDocument(String collection, String key, String json) {
        return submit(() -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "INSERT OR REPLACE INTO documents (collection, key, value) VALUES (?, ?, ?)")) {
                update.setString(1, collection);
                update.setString(2, key);
                update.setString(3, json);
                update.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteDocument(String collection, String key) {
        return submit(() -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "DELETE FROM documents WHERE collection = ? AND key = ?")) {
                update.setString(1, collection);
                update.setString(2, key);
                update.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void close() {
        executor.execute(() -> {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot close the database", e);
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Storage did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static CaptivityRecord read(ResultSet row) throws SQLException {
        String captor = row.getString("captor");
        String cellWorld = row.getString("cell_world");
        return new CaptivityRecord(
                new TargetId(UUID.fromString(row.getString("target"))),
                TargetKind.valueOf(row.getString("kind")),
                captor == null ? Optional.empty() : Optional.of(new TargetId(UUID.fromString(captor))),
                row.getDouble("resistance"),
                row.getInt("broken") != 0,
                row.getLong("captured_at"),
                new Location(row.getString("world"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z")),
                cellWorld == null ? Optional.empty() : Optional.of(new BlockLocation(
                        cellWorld, row.getInt("cell_x"), row.getInt("cell_y"), row.getInt("cell_z"))));
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlTask<T> {
        T run() throws SQLException;
    }
}
