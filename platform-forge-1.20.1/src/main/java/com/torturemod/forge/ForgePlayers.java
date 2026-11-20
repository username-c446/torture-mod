package com.torturemod.forge;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.util.List;
import java.util.Optional;

final class ForgePlayers implements Players {
    private final MinecraftServer server;
    private final ForgeEntities entities;

    ForgePlayers(MinecraftServer server, ForgeEntities entities) {
        this.server = server;
        this.entities = entities;
    }

    @Override
    public List<TargetState> online() {
        return server.getPlayerList().getPlayers().stream().map(ForgeTargets::toState).toList();
    }

    @Override
    public Optional<BlockLocation> respawnPoint(TargetId player) {
        return player(player).flatMap(found -> {
            BlockPos pos = found.getRespawnPosition();
            return pos == null ? Optional.empty() : Optional.of(new BlockLocation(
                    found.getRespawnDimension().location().toString(), pos.getX(), pos.getY(), pos.getZ()));
        });
    }

    @Override
    public Optional<BlockLocation> lastDeath(TargetId player) {
        return player(player).flatMap(ServerPlayer::getLastDeathLocation).map(death -> new BlockLocation(
                death.dimension().location().toString(), death.pos().getX(), death.pos().getY(), death.pos().getZ()));
    }

    @Override
    public Optional<String> team(TargetId player) {
        return player(player).map(ServerPlayer::getTeam).map(Team::getName);
    }

    @Override
    public List<String> teamMembers(String team) {
        PlayerTeam found = server.getScoreboard().getPlayerTeam(team);
        return found == null ? List.of() : found.getPlayers().stream().sorted().toList();
    }

    private Optional<ServerPlayer> player(TargetId id) {
        return entities.find(id).filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast);
    }
}
