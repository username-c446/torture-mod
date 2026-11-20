package com.torturemod.api.port;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.List;
import java.util.Optional;

public interface Targets {
    /** Пусто, если цель не загружена в мир или игрок не в сети. */
    Optional<TargetState> state(TargetId id);

    /** Живые сущности в параллелепипеде, границы включительно. */
    List<TargetState> livingIn(String worldId, BlockLocation min, BlockLocation max);

    List<TargetState> playersNear(Location center, double radius);

    /** Ник или UUID игрока, в том числе того, кто не в сети, но уже заходил на сервер. */
    Optional<TargetId> resolvePlayer(String nameOrUuid);

    /** Ник игрока, в том числе того, кто не в сети. */
    Optional<String> playerName(TargetId player);

    /** Запрещает мобу исчезать при удалении от игроков. */
    void setPersistent(TargetId id);

    /** Видит ли один другого: оба в мире и между ними нет непрозрачных блоков. */
    boolean canSee(TargetId observer, TargetId target);
}
