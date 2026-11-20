package com.torturemod.feature.minions;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;

import java.util.List;
import java.util.Optional;

/** Поручение курьеру или жителю: взять в одном месте, отнести в другое. */
final class Job {
    enum Kind {
        /** Награда из сундука наград пленному. */
        DELIVER,
        /** Сданное из сундука сдачи на склад. */
        HAUL,
        /** Отнести то, что моб уже несёт, в сундук. */
        UNLOAD
    }

    enum Stage {
        TO_SOURCE,
        TO_TARGET
    }

    final Kind kind;
    final Optional<BlockLocation> source;
    final Optional<BlockLocation> chestTarget;
    final Optional<TargetId> captiveTarget;
    final List<ItemStackData> wanted;
    Stage stage;
    long startedAt;
    int failures;

    Job(Kind kind, Optional<BlockLocation> source, Optional<BlockLocation> chestTarget, Optional<TargetId> captiveTarget,
        List<ItemStackData> wanted, Stage stage, long startedAt) {
        this.kind = kind;
        this.source = source;
        this.chestTarget = chestTarget;
        this.captiveTarget = captiveTarget;
        this.wanted = List.copyOf(wanted);
        this.stage = stage;
        this.startedAt = startedAt;
    }
}
