package com.torturemod.api.port;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.List;
import java.util.Optional;

/** Сведения об игроках. Для пленного не в сети отвечает его тело. */
public interface Players {
    List<TargetState> online();

    /** Точка возрождения, например кровать. */
    Optional<BlockLocation> respawnPoint(TargetId player);

    Optional<BlockLocation> lastDeath(TargetId player);

    /** Ванильная команда (/team). */
    Optional<String> team(TargetId player);

    /** Ники участников команды. */
    List<String> teamMembers(String team);
}
