package com.torturemod.feature.minions;

import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

import java.util.ArrayList;
import java.util.List;

/** Моб на службе тюрьмы. */
final class Minion {
    enum Mode {
        /** Охраняет тюрьму и не выходит за её границы. */
        POST,
        /** Идёт за хозяином куда угодно. */
        FOLLOW,
        /** Носит награды и сданное. */
        COURIER
    }

    final TargetId id;
    final String typeId;
    final String prisonId;
    TargetId owner;
    Mode mode;
    Location post;
    /** Что моб несёт сейчас: при перезапуске не теряется. */
    final List<ItemStackData> carrying = new ArrayList<>();

    Minion(TargetId id, String typeId, String prisonId, TargetId owner, Mode mode, Location post) {
        this.id = id;
        this.typeId = typeId;
        this.prisonId = prisonId;
        this.owner = owner;
        this.mode = mode;
        this.post = post;
    }

    boolean isVillager() {
        return typeId.equals("minecraft:villager");
    }
}
