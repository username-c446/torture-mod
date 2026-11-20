package com.torturemod.storage.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SqliteStorageTest {
    @Test
    void recordSurvivesReopenAndCanBeDeleted(@TempDir Path directory) {
        Path file = directory.resolve("nested/test.db");
        TargetId target = new TargetId(UUID.randomUUID());
        CaptivityRecord record = new CaptivityRecord(target, TargetKind.PLAYER, Optional.empty(), 42.5, true, 1000L,
                new Location("minecraft:overworld", 1, 2, 3),
                Optional.of(new BlockLocation("minecraft:overworld", 1, 2, 3)));

        SqliteStorage first = new SqliteStorage(file);
        first.open().join();
        first.save(record).join();
        first.close();

        SqliteStorage second = new SqliteStorage(file);
        second.open().join();
        assertEquals(Optional.of(record), second.load(target).join());
        assertEquals(List.of(record), second.loadAll().join());
        second.delete(target).join();
        assertTrue(second.load(target).join().isEmpty());
        second.close();
    }
}
