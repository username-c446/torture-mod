package com.torturemod.feature.prison;

import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;

import java.util.List;

/** Сделка выкупа, которую тюрьма подтверждает вручную. Залог лежит у мода до ответа. */
final class Deal {
    enum Status {
        PENDING,
        /** Отказано или истекло: покупатель забирает залог у сундука выкупа. */
        REJECTED
    }

    final String id;
    final String prisonId;
    final TargetId captive;
    final TargetId buyer;
    final List<ItemStackData> escrow;
    final long createdAt;
    Status status;

    Deal(String id, String prisonId, TargetId captive, TargetId buyer, List<ItemStackData> escrow, long createdAt,
         Status status) {
        this.id = id;
        this.prisonId = prisonId;
        this.captive = captive;
        this.buyer = buyer;
        this.escrow = List.copyOf(escrow);
        this.createdAt = createdAt;
        this.status = status;
    }
}
