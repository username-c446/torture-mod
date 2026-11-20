package com.torturemod.api.prison;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Тюрьмы для других фич и интеграций. Вызывается на основном потоке сервера. */
public interface PrisonService {
    Collection<PrisonInfo> prisons();

    Optional<PrisonInfo> prison(String prisonId);

    Optional<PrisonInfo> prisonAt(Location location);

    /** Тюрьма, к которой приписан пленный. */
    Optional<String> prisonOf(TargetId captive);

    /** Есть ли у игрока это право в команде тюрьмы. У владельца есть все. */
    boolean hasRight(String prisonId, TargetId player, PrisonRight right);

    /** Команда тюрьмы и союзники владельца по ванильной команде: охрана их не атакует. */
    boolean isFriend(String prisonId, TargetId player);

    /** Игроки-охранники в сети. */
    List<TargetId> onlineGuards(String prisonId);

    List<BlockLocation> chests(String prisonId, ChestRole role);

    /** Расширение владения: модули приватов (Towny и подобные) дают роли в тюрьме. */
    void addOwnershipSource(OwnershipSource source);

    /** Раздел пульта от другой фичи. */
    void addConsoleSection(ConsoleSection section);
}
