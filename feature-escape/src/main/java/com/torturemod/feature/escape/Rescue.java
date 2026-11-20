package com.torturemod.feature.escape;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Освобождение помощником: ПКМ ножницами по пленному снова и снова, пока не пройдёт нужное время.
 * Прерывается, если помощник перестал резать, отошёл или его ранили.
 */
final class Rescue {
    private static final int CHECK_INTERVAL_TICKS = 5;

    private static final class Session {
        final TargetId captive;
        final String itemId;
        final long startedAt;
        long lastClickAt;

        Session(TargetId captive, String itemId, long now) {
            this.captive = captive;
            this.itemId = itemId;
            this.startedAt = now;
            this.lastClickAt = now;
        }
    }

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final Map<TargetId, Session> sessions = new HashMap<>();
    private long ticks;

    Rescue(FeatureContext context, CaptivityService captivity) {
        this.context = context;
        this.captivity = captivity;
    }

    void onInteract(GameEvent.EntityInteracted event) {
        if (event.cancellation().isCancelled() || event.itemId().isEmpty()
                || !items().contains(event.itemId().get())
                || !captivity.isCaptive(event.target()) || captivity.isCaptive(event.actor())) {
            return;
        }
        event.cancellation().cancel();
        long now = context.clock().millis();
        Session session = sessions.get(event.actor());
        if (session == null || !session.captive.equals(event.target())) {
            session = new Session(event.target(), event.itemId().get(), now);
            sessions.put(event.actor(), session);
            context.platform().feedback().message(event.target(),
                    context.messages().text("escape.rescue.captive", name(event.actor())));
        } else {
            session.lastClickAt = now;
        }
        long duration = durationMillis();
        if (now - session.startedAt >= duration) {
            complete(event.actor(), session);
            return;
        }
        long percent = Math.min(99, (now - session.startedAt) * 100 / duration);
        context.platform().feedback().actionBar(event.actor(),
                context.messages().text("escape.rescue.progress", name(event.target()), percent));
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
        long gap = (long) (context.config().getDouble("escape.rescue.click_gap_seconds", 1.5) * 1000);
        double maxDistance = context.config().getDouble("escape.rescue.max_distance", 4);
        for (Map.Entry<TargetId, Session> entry : List.copyOf(sessions.entrySet())) {
            Optional<TargetState> helper = context.platform().targets().state(entry.getKey());
            Optional<TargetState> captive = context.platform().targets().state(entry.getValue().captive);
            boolean tooFar = helper.isEmpty() || captive.isEmpty()
                    || distance(helper.get().location(), captive.get().location()) > maxDistance;
            if (now - entry.getValue().lastClickAt > gap || tooFar) {
                interrupt(entry.getKey());
            }
        }
    }

    private void complete(TargetId helper, Session session) {
        sessions.remove(helper);
        if (!captivity.release(session.captive, ReleaseReason.ESCAPE)) {
            return;
        }
        context.platform().items().findInHands(helper, session.itemId)
                .ifPresent(slot -> context.platform().items().spend(helper, slot));
        context.platform().feedback().message(helper, context.messages().text("escape.rescue.done", name(session.captive)));
    }

    private void interrupt(TargetId helper) {
        if (sessions.remove(helper) != null) {
            context.platform().feedback().actionBar(helper, context.messages().text("escape.rescue.interrupted"));
        }
    }

    private List<String> items() {
        return context.config().has("escape.rescue.items")
                ? context.config().getStringList("escape.rescue.items")
                : List.of("minecraft:shears");
    }

    private long durationMillis() {
        return (long) (context.config().getDouble("escape.rescue.seconds", 5) * 1000);
    }

    private String name(TargetId id) {
        return context.platform().targets().state(id).map(TargetState::name)
                .or(() -> context.platform().targets().playerName(id)).orElse(id.value().toString());
    }

    private static double distance(Location a, Location b) {
        if (!a.worldId().equals(b.worldId())) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
