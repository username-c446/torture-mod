package com.torturemod.feature.cuffs;

import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.help.Hints;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.prison.PrisonRight;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.api.resistance.ResistanceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Надевание, снятие и перекусывание наручников: несколько ПКМ по пленному подряд, стоя рядом.
 * Прерывается, если нажатия кончились, участники разошлись или того, кто действует, ранили.
 */
final class CuffSessions {
    private static final int CHECK_INTERVAL_TICKS = 5;

    enum Mode {
        /** Цепью надевают. */
        CUFF,
        /** Член команды или палач снимает пустой рукой, цепь возвращается. */
        UNCUFF,
        /** Помощник перекусывает ножницами, цепь ломается. */
        CUT
    }

    private static final class Session {
        final TargetId captive;
        final Mode mode;
        final String itemId;
        final long startedAt;
        final long duration;
        long lastClickAt;

        Session(TargetId captive, Mode mode, String itemId, long now, long duration) {
            this.captive = captive;
            this.mode = mode;
            this.itemId = itemId;
            this.startedAt = now;
            this.lastClickAt = now;
            this.duration = duration;
        }
    }

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final ResistanceService resistance;
    private final Cuffs cuffs;
    private final Map<TargetId, Session> sessions = new HashMap<>();
    private long ticks;

    CuffSessions(FeatureContext context, CaptivityService captivity, ResistanceService resistance, Cuffs cuffs) {
        this.context = context;
        this.captivity = captivity;
        this.resistance = resistance;
        this.cuffs = cuffs;
    }

    void onInteract(GameEvent.EntityInteracted event) {
        if (event.cancellation().isCancelled() || event.sneaking() || captivity.isCaptive(event.actor())) {
            return;
        }
        Optional<Captive> captive = captivity.find(event.target());
        if (captive.isEmpty() || captive.get().kind() != com.torturemod.api.model.TargetKind.PLAYER) {
            return;
        }
        Optional<Mode> mode = modeFor(event, captive.get());
        if (mode.isEmpty()) {
            return;
        }
        event.cancellation().cancel();
        if (!permitted(event.actor(), captive.get(), mode.get())) {
            bar(event.actor(), text("cuffs.no_right"));
            return;
        }
        long now = context.clock().millis();
        Session session = sessions.get(event.actor());
        if (session == null || !session.captive.equals(event.target()) || session.mode != mode.get()) {
            session = new Session(event.target(), mode.get(), event.itemId().orElse(""), now,
                    durationMillis(mode.get(), event.target()));
            sessions.put(event.actor(), session);
            context.platform().feedback().message(event.target(),
                    text("cuffs.started." + mode.get().name(), name(event.actor())));
        } else {
            session.lastClickAt = now;
        }
        if (now - session.startedAt >= session.duration) {
            complete(event.actor(), session);
            return;
        }
        long percent = Math.min(99, (now - session.startedAt) * 100 / Math.max(1, session.duration));
        bar(event.actor(), text("cuffs.progress." + mode.get().name(), name(event.target()), percent));
    }

    private Optional<Mode> modeFor(GameEvent.EntityInteracted event, Captive captive) {
        boolean cuffed = cuffs.isCuffed(captive.target());
        Optional<String> item = event.itemId();
        if (item.isPresent() && !cuffed && cuffItems().contains(item.get())) {
            return Optional.of(Mode.CUFF);
        }
        // Событие приходит для каждой руки: снимает только тот, у кого пуста основная рука.
        if (item.isEmpty() && cuffed && context.platform().items().heldItem(event.actor()).isEmpty()) {
            return Optional.of(Mode.UNCUFF);
        }
        if (item.isPresent() && cuffed && cutItems().contains(item.get())) {
            return Optional.of(Mode.CUT);
        }
        return Optional.empty();
    }

    /**
     * Надеть может тот, у кого есть право на пленного. Снять пустой рукой — команда тюрьмы с правом
     * на наручники, а вне тюрьмы палач или тот, кто их надел. Перекусить может любой, кроме пленных.
     */
    private boolean permitted(TargetId actor, Captive captive, Mode mode) {
        Optional<TargetState> where = context.platform().targets().state(captive.target());
        if (where.isEmpty()) {
            return false;
        }
        return switch (mode) {
            case CUFF -> captivity.allows(actor, AccessRight.CUFF, Optional.of(captive.target()), where.get().location());
            case UNCUFF -> {
                Optional<PrisonService> prisons = context.services().find(PrisonService.class);
                Optional<String> prison = prisons.flatMap(service -> service.prisonOf(captive.target()));
                if (prison.isPresent()) {
                    yield prisons.get().hasRight(prison.get(), actor, PrisonRight.CUFF);
                }
                yield captive.captor().filter(actor::equals).isPresent()
                        || cuffs.cuffedBy(captive.target()).filter(actor::equals).isPresent();
            }
            case CUT -> true;
        };
    }

    void onDamaged(GameEvent.Damaged event) {
        if (sessions.containsKey(event.target())) {
            interrupt(event.target());
        }
    }

    void forget(TargetId captive) {
        sessions.values().removeIf(session -> session.captive.equals(captive));
    }

    void tick() {
        if (++ticks % CHECK_INTERVAL_TICKS != 0 || sessions.isEmpty()) {
            return;
        }
        long now = context.clock().millis();
        long gap = (long) (context.config().getDouble("cuffs.click_gap_seconds", 1.5) * 1000);
        double maxDistance = context.config().getDouble("cuffs.max_distance", 4);
        for (Map.Entry<TargetId, Session> entry : List.copyOf(sessions.entrySet())) {
            Optional<TargetState> actor = context.platform().targets().state(entry.getKey());
            Optional<TargetState> captive = context.platform().targets().state(entry.getValue().captive);
            boolean tooFar = actor.isEmpty() || captive.isEmpty()
                    || distance(actor.get().location(), captive.get().location()) > maxDistance;
            if (now - entry.getValue().lastClickAt > gap || tooFar) {
                interrupt(entry.getKey());
            }
        }
    }

    private void complete(TargetId actor, Session session) {
        sessions.remove(actor);
        String captiveName = name(session.captive);
        switch (session.mode) {
            case CUFF -> {
                context.platform().items().consume(actor, session.itemId, 1);
                cuffs.cuff(session.captive, Optional.of(actor));
                bar(actor, text("cuffs.done.CUFF", captiveName));
            }
            case UNCUFF -> {
                if (cuffs.uncuff(session.captive)) {
                    context.platform().inventories().give(actor, List.of(ItemStackData.of(cuffItems().get(0), 1)));
                    bar(actor, text("cuffs.done.UNCUFF", captiveName));
                }
            }
            case CUT -> {
                if (cuffs.uncuff(session.captive)) {
                    context.platform().items().findInHands(actor, session.itemId)
                            .ifPresent(slot -> context.platform().items().spend(actor, slot));
                    bar(actor, text("cuffs.done.CUT", captiveName));
                    context.services().find(Hints.class).ifPresent(hints -> hints.hint(actor, "hint.cuffs.cut"));
                }
            }
        }
    }

    private void interrupt(TargetId actor) {
        if (sessions.remove(actor) != null) {
            bar(actor, text("cuffs.interrupted"));
        }
    }

    /** На поводке и сломленного заковать быстрее: он не сопротивляется. */
    private long durationMillis(Mode mode, TargetId captive) {
        double seconds = mode == Mode.CUT
                ? context.config().getDouble("cuffs.cut_seconds", 5)
                : context.config().getDouble("cuffs.seconds", 3);
        if (mode == Mode.CUFF && (resistance.isBroken(captive) || captivity.isCellChecksSuspended(captive))) {
            seconds *= Math.max(0, Math.min(1, context.config().getDouble("cuffs.helpless_factor", 0.5)));
        }
        return (long) (seconds * 1000);
    }

    private List<String> cuffItems() {
        return context.config().has("cuffs.items") && !context.config().getStringList("cuffs.items").isEmpty()
                ? context.config().getStringList("cuffs.items")
                : List.of(CuffsFeature.HANDCUFFS);
    }

    private List<String> cutItems() {
        return context.config().has("escape.rescue.items")
                ? context.config().getStringList("escape.rescue.items")
                : List.of("minecraft:shears");
    }

    private String name(TargetId id) {
        return context.platform().targets().state(id).map(TargetState::name)
                .or(() -> context.platform().targets().playerName(id)).orElse(id.value().toString());
    }

    private void bar(TargetId receiver, String text) {
        context.platform().feedback().actionBar(receiver, text);
    }

    private String text(String key, Object... args) {
        return context.messages().text(key, args);
    }

    private static double distance(Location a, Location b) {
        if (!a.worldId().equals(b.worldId())) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
