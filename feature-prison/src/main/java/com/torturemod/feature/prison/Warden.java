package com.torturemod.feature.prison;

import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptiveAction;
import com.torturemod.api.captivity.Containment;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.EscapeHelped;
import com.torturemod.api.prison.PrisonEscaped;
import com.torturemod.api.prison.PrisonGuards;
import com.torturemod.api.prison.PrisonRight;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Удержание в тюрьме: приписка пленных, побег за границы с тревогой охране, срок, права команды
 * на пленных и флаги зон для пленного без наручников.
 */
final class Warden implements Containment {
    /** Флаги зон решают после наручников, но раньше свободных рук вне тюрьмы. */
    static final int ZONE_RULE_PRIORITY = 500;
    static final int CHECK_INTERVAL_TICKS = 20;
    private static final long CHECK_MILLIS = CHECK_INTERVAL_TICKS * 50L;

    private final Support s;
    private final Deposits deposits;
    private long ticks;

    Warden(Support support, Deposits deposits) {
        this.s = support;
        this.deposits = deposits;
    }

    // ---------- удержание ----------

    @Override
    public boolean claims(Captive captive) {
        return s.registry.inmates.containsKey(captive.target());
    }

    @Override
    public boolean holds(Captive captive, Location location) {
        return s.registry.at(location).isPresent();
    }

    // ---------- права и флаги ----------

    Optional<Boolean> access(TargetId actor, AccessRight right, Optional<Captive> captive, Location where) {
        Optional<Prison> prison = captive.flatMap(found -> s.registry.ofInmate(found.target()))
                .or(() -> s.registry.at(where));
        return prison.map(found -> s.registry.has(found, actor, PrisonRight.valueOf(right.name())));
    }

    Optional<Boolean> act(Captive captive, CaptiveAction action, BlockLocation where) {
        Optional<Prison> prison = s.registry.ofInmate(captive.target());
        if (prison.isEmpty() || !prison.get().area.contains(where)) {
            return Optional.empty();
        }
        if (action == CaptiveAction.USE_BLOCK && prison.get().chests.get(where) == ChestRole.DEPOSIT) {
            return Optional.of(true);
        }
        return Optional.of(prison.get().flagsAt(where).contains(Flag.of(action)));
    }

    // ---------- события ----------

    void onCaptured(Captive captive) {
        s.context.platform().targets().state(captive.target())
                .flatMap(state -> s.registry.at(state.location()))
                .ifPresent(prison -> assign(captive.target(), prison));
    }

    void onReleased(TargetId captive) {
        s.registry.removeInmate(captive);
    }

    /** Ножницы чужого по пленному тюрьмы: охрана считает его пособником, а в тюрьме ножницы не освобождают. */
    void onInteract(GameEvent.EntityInteracted event) {
        Optional<Prison> prison = s.registry.ofInmate(event.target());
        if (prison.isEmpty() || event.itemId().isEmpty() || !rescueItems().contains(event.itemId().get())
                || s.captivity.isCaptive(event.actor())) {
            return;
        }
        if (!s.registry.isFriend(prison.get(), event.actor())) {
            s.context.bus().publish(new EscapeHelped(prison.get().id, event.actor(), event.target()));
        }
        if (!event.cancellation().isCancelled()) {
            event.cancellation().cancel();
            s.bar(event.actor(), "prison.rescue_blocked");
        }
    }

    /** Чужой напал на команду в тюрьме: для охраны он пособник. */
    void onDamaged(GameEvent.Damaged event) {
        if (event.source().isEmpty() || s.captivity.isCaptive(event.target())) {
            return;
        }
        Optional<TargetState> victim = s.context.platform().targets().state(event.target());
        if (victim.isEmpty() || victim.get().kind() != TargetKind.PLAYER) {
            return;
        }
        s.registry.at(victim.get().location())
                .filter(prison -> s.registry.isFriend(prison, event.target())
                        && !s.registry.isFriend(prison, event.source().get()))
                .ifPresent(prison -> s.context.bus().publish(
                        new EscapeHelped(prison.id, event.source().get(), event.target())));
    }

    // ---------- тик ----------

    void tick() {
        if (++ticks % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (Captive captive : List.copyOf(s.captivity.captives())) {
            Optional<TargetState> state = s.context.platform().targets().state(captive.target());
            if (state.isEmpty() || s.captivity.isCellChecksSuspended(captive.target())) {
                continue;
            }
            Inmate inmate = s.registry.inmates.get(captive.target());
            Optional<Prison> here = s.registry.at(state.get().location());
            if (inmate == null) {
                here.ifPresent(prison -> assign(captive.target(), prison));
                continue;
            }
            Optional<Prison> own = s.registry.byId(inmate.prisonId);
            if (own.isPresent() && own.get().area.contains(state.get().location())) {
                serve(captive, inmate, own.get());
            } else if (here.isPresent()) {
                // Пленного привели в другую тюрьму: теперь он её.
                assign(captive.target(), here.get());
            } else {
                escape(captive, own, state.get());
            }
        }
    }

    private void assign(TargetId captive, Prison prison) {
        Inmate inmate = s.registry.inmates.get(captive);
        if (inmate != null && inmate.prisonId.equals(prison.id)) {
            return;
        }
        if (inmate == null) {
            inmate = new Inmate(prison.id);
            s.registry.inmates.put(captive, inmate);
        } else {
            inmate.prisonId = prison.id;
            inmate.progress.clear();
            inmate.price.clear();
            inmate.quotaOverride.clear();
        }
        s.registry.saveInmate(captive);
        s.message(captive, "prison.inmate.assigned", prison.name);
        s.hint(captive, "hint.prison.inmate");
    }

    /** Время внутри тюрьмы: срок и игровой день нормы идут, пока пленный онлайн. */
    private void serve(Captive captive, Inmate inmate, Prison prison) {
        boolean dirty = false;
        if (inmate.hasSentence()) {
            inmate.sentenceLeftMillis = Math.max(0, inmate.sentenceLeftMillis - CHECK_MILLIS);
            dirty = true;
            if (inmate.sentenceLeftMillis == 0) {
                s.message(captive.target(), "prison.sentence.served", prison.name);
                s.captivity.release(captive.target(), ReleaseReason.SENTENCE_SERVED);
                return;
            }
        }
        dirty |= deposits.advanceDay(captive.target(), inmate);
        // Запись раз в минуту: при сбое теряется не больше минуты срока.
        if (dirty && ticks % (CHECK_INTERVAL_TICKS * 60) == 0) {
            s.registry.saveInmate(captive.target());
        }
    }

    private void escape(Captive captive, Optional<Prison> prison, TargetState state) {
        if (prison.isEmpty()) {
            s.registry.removeInmate(captive.target());
            return;
        }
        boolean seen = seenByGuards(prison.get(), captive.target(), state.location());
        if (seen) {
            Location where = state.location();
            String coordinates = Math.round(where.x()) + " " + Math.round(where.y()) + " " + Math.round(where.z());
            for (TargetId guard : s.registry.onlineGuards(prison.get().id)) {
                s.message(guard, "prison.alarm", s.name(captive.target()), prison.get().name, coordinates);
            }
        }
        s.context.bus().publish(new PrisonEscaped(prison.get().id, captive.target(), state.location(), seen));
        if (captive.kind() == TargetKind.PLAYER) {
            s.message(captive.target(), seen ? "prison.escaped.seen" : "prison.escaped.unseen", prison.get().name);
        }
        s.captivity.release(captive.target(), ReleaseReason.ESCAPE);
    }

    /** Беглеца видит охранник: игрок команды или союзник рядом без стен между ними, либо моб-охранник. */
    private boolean seenByGuards(Prison prison, TargetId captive, Location where) {
        double distance = s.settings().alarmDistance();
        for (TargetId guard : s.registry.onlineGuards(prison.id)) {
            Optional<TargetState> state = s.context.platform().targets().state(guard);
            if (state.isPresent() && distance(state.get().location(), where) <= distance
                    && s.context.platform().targets().canSee(guard, captive)) {
                return true;
            }
        }
        return s.context.services().find(PrisonGuards.class)
                .filter(guards -> guards.anyGuardSees(prison.id, captive, distance)).isPresent();
    }

    private List<String> rescueItems() {
        return s.context.config().has("escape.rescue.items")
                ? s.context.config().getStringList("escape.rescue.items")
                : List.of("minecraft:shears");
    }

    static double distance(Location a, Location b) {
        if (!a.worldId().equals(b.worldId())) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Срок пленного в минутах для меню; пусто, если срока нет. */
    static Optional<Long> sentenceMinutes(Map<TargetId, Inmate> inmates, TargetId captive) {
        Inmate inmate = inmates.get(captive);
        return inmate == null || !inmate.hasSentence() ? Optional.empty()
                : Optional.of((inmate.sentenceLeftMillis + 59_999) / 60_000);
    }
}
