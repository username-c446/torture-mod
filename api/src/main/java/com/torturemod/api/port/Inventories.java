package com.torturemod.api.port;

import com.torturemod.api.model.InventoryKind;
import com.torturemod.api.model.ItemEntry;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;

import java.util.List;

public interface Inventories {
    List<ItemEntry> contents(TargetId owner, InventoryKind kind);

    /** Перекладывает вещь из слота пленного палачу. */
    boolean take(TargetId taker, TargetId owner, InventoryKind kind, int slot);

    /** Кладёт вещи игроку; что не поместилось, падает у его ног. */
    void give(TargetId player, List<ItemStackData> stacks);
}
