package com.torturemod.feature.prison;

import com.torturemod.api.model.Area;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.PrisonInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Тюрьма: границы, команда, зоны, сундуки, пульты, норма и награды. Меняется на основном потоке. */
final class Prison {
    final String id;
    String name;
    TargetId owner;
    final Area area;
    /** Игрок → ID роли. Владелец сюда не входит. */
    final Map<TargetId, String> members = new LinkedHashMap<>();
    /** Свои роли тюрьмы, без пресетов. */
    final Map<String, Role> customRoles = new LinkedHashMap<>();
    final List<Zone> zones = new ArrayList<>();
    final Set<Flag> baseFlags = EnumSet.of(Flag.EAT);
    final Map<String, Integer> quota = new LinkedHashMap<>();
    final Map<BlockLocation, ChestRole> chests = new LinkedHashMap<>();
    final Set<BlockLocation> consoles = new LinkedHashSet<>();
    boolean confirmDeals;
    int rewardSentencePercent;
    double rewardRecoveryMultiplier = 1;
    /** ID предмета → сколько выдать за выполненную норму. */
    final Map<String, Integer> rewardItems = new LinkedHashMap<>();

    Prison(String id, String name, TargetId owner, Area area) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.area = area;
    }

    PrisonInfo info() {
        return new PrisonInfo(id, name, owner, area);
    }

    Optional<Role> role(String roleId) {
        Role preset = Role.PRESETS.get(roleId);
        return preset != null ? Optional.of(preset) : Optional.ofNullable(customRoles.get(roleId));
    }

    List<Role> allRoles() {
        List<Role> roles = new ArrayList<>(Role.PRESETS.values().stream()
                .sorted(Comparator.comparingInt(role -> List.of(Role.OWNER, Role.COOWNER, Role.WARDEN).indexOf(role.id)))
                .toList());
        roles.addAll(customRoles.values());
        return roles;
    }

    /** Зона с наибольшим приоритетом, в которой лежит блок. */
    Optional<Zone> zoneAt(BlockLocation block) {
        return zones.stream().filter(zone -> zone.area.contains(block))
                .max(Comparator.comparingInt(zone -> zone.priority));
    }

    Optional<Zone> zoneAt(Location location) {
        return zoneAt(BlockLocation.of(location));
    }

    Set<Flag> flagsAt(BlockLocation block) {
        return zoneAt(block).map(zone -> zone.flags).orElse(baseFlags);
    }

    List<BlockLocation> chests(ChestRole role) {
        return chests.entrySet().stream().filter(entry -> entry.getValue() == role).map(Map.Entry::getKey).toList();
    }
}
