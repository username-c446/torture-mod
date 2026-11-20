package com.torturemod.api.event;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.TargetId;

import java.util.Optional;

/** Входящие события платформы на языке ядра. Действия игрока можно отменить через {@link Cancellation}. */
public sealed interface GameEvent {
    record PlayerJoined(TargetId player) implements GameEvent {
    }

    record PlayerLeft(TargetId player) implements GameEvent {
    }

    /** Взаимодействие с сущностью. Приходит для каждой руки со своим предметом. */
    record EntityInteracted(TargetId actor, TargetId target, Optional<String> itemId, boolean sneaking,
                            Cancellation cancellation)
            implements GameEvent {
    }

    /** Взаимодействие с блоком. Приходит для каждой руки со своим предметом. */
    record BlockInteracted(TargetId actor, BlockLocation block, Optional<String> itemId, boolean sneaking,
                           Cancellation cancellation) implements GameEvent {
    }

    /** ЛКМ по блоку: начало ломания. */
    record BlockAttacked(TargetId actor, BlockLocation block, Optional<String> itemId, boolean sneaking,
                         Cancellation cancellation) implements GameEvent {
    }

    /** ЛКМ в воздух: взмах рукой, который не задел ни блок, ни сущность. */
    record ArmSwung(TargetId actor, Optional<String> itemId, boolean sneaking) implements GameEvent {
    }

    /** Использование предмета в воздухе: еда, зелья, жемчуг и подобное. */
    record ItemUsed(TargetId actor, String itemId, boolean sneaking, Cancellation cancellation) implements GameEvent {
    }

    record ItemDropped(TargetId actor, String itemId, Cancellation cancellation) implements GameEvent {
    }

    record AttackAttempted(TargetId actor, TargetId target, Cancellation cancellation) implements GameEvent {
    }

    record BlockBroken(TargetId actor, BlockLocation block, Cancellation cancellation) implements GameEvent {
    }

    record BlockPlaced(TargetId actor, BlockLocation block, Cancellation cancellation) implements GameEvent {
    }

    record Damaged(TargetId target, Optional<TargetId> source, float amount, Cancellation cancellation)
            implements GameEvent {
    }

    record Died(TargetId target) implements GameEvent {
    }

    /** Сущность хочет слезть с транспорта. Отмена оставляет её на месте. */
    record DismountAttempted(TargetId rider, TargetId vehicle, Cancellation cancellation) implements GameEvent {
    }

    record ChatSent(TargetId player, String message, Cancellation cancellation) implements GameEvent {
    }

    /** @param command текст команды без косой черты */
    record CommandIssued(TargetId player, String command, Cancellation cancellation) implements GameEvent {
    }

    record InputPressed(TargetId player, Input input) implements GameEvent {
    }

    enum Input {
        JUMP,
        SNEAK
    }
}
