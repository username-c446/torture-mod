package com.torturemod.feature.convoy;

import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Convoy;
import com.torturemod.api.port.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Конвой: поводок и транспорт. Пока пленного ведут или везут, клетка его не держит. Когда поводок
 * отпущен, клетка, в которой он стоит, становится новой; иначе идёт таймер возврата. Нужен плен.
 * Состояние конвоя живёт в памяти: после перезапуска пленный, оставшийся вне клетки, освобождается.
 */
public final class ConvoyFeature implements Feature {
    private static final int PULL_INTERVAL_TICKS = 2;
    private static final int TIMER_INTERVAL_TICKS = 20;

    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    /** Пленный → палач, который его ведёт. */
    private final Map<TargetId, TargetId> leashes = new HashMap<>();
    /** Пленный → когда истекает время вернуть его в клетку. */
    private final Map<TargetId, Long> returnDeadlines = new HashMap<>();
    /** Пленные, которых мод сам снимает с транспорта: их слезание не отменяется. */
    private final Set<TargetId> dismounting = new HashSet<>();
    private FeatureContext context;
    private CaptivityService captivity;
    private Scheduler.Task tickTask;
    private long ticks;

    @Override
    public String id() {
        return "convoy";
    }

    @Override
    public void start(FeatureContext context) {
        this.context = context;
        captivity = context.services().find(CaptivityService.class)
                .orElseThrow(() -> new IllegalStateException("Convoy needs the captivity feature started first"));
        EventBus bus = context.bus();
        subscriptions.add(bus.subscribe(GameEvent.EntityInteracted.class, this::onInteract));
        subscriptions.add(bus.subscribe(GameEvent.DismountAttempted.class, event -> {
            if ((leashes.containsKey(event.rider()) || returnDeadlines.containsKey(event.rider()))
                    && !dismounting.contains(event.rider())) {
                event.cancellation().cancel();
            }
        }));
        subscriptions.add(bus.subscribe(CaptiveReleased.class, event -> forget(event.captive().target())));
        tickTask = context.platform().scheduler().everyTick(this::tick);
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (tickTask != null) {
            tickTask.cancel();
        }
        List.copyOf(leashes.keySet()).forEach(captive -> convoy().hideLeash(captive));
    }

    private void onInteract(GameEvent.EntityInteracted event) {
        if (event.cancellation().isCancelled() || captivity.isCaptive(event.actor())) {
            return;
        }
        boolean leashItem = event.itemId().filter(leashItems()::contains).isPresent();
        if (leashItem && captivity.isCaptive(event.target())) {
            event.cancellation().cancel();
            Optional<TargetState> where = context.platform().targets().state(event.target());
            if (where.isPresent() && captivity.allows(event.actor(), AccessRight.CONVOY,
                    Optional.of(event.target()), where.get().location())) {
                toggleLeash(event.actor(), event.target());
            } else {
                bar(event.actor(), text("convoy.no_right"));
            }
            return;
        }
        Optional<TargetId> led = ledBy(event.actor());
        if (led.isPresent() && convoy().isVehicle(event.target())) {
            event.cancellation().cancel();
            boolean seated = convoy().mount(led.get(), event.target());
            bar(event.actor(), seated ? text("convoy.mounted", name(led.get())) : text("convoy.no_seat"));
        }
    }

    private void toggleLeash(TargetId actor, TargetId captive) {
        TargetId holder = leashes.get(captive);
        if (holder == null) {
            leashes.put(captive, actor);
            returnDeadlines.remove(captive);
            captivity.setCellChecksSuspended(captive, true);
            convoy().showLeash(captive, actor);
            bar(actor, text("convoy.leashed", name(captive)));
            context.platform().feedback().message(captive, text("convoy.you_are_led"));
        } else if (holder.equals(actor)) {
            if (convoy().vehicleOf(captive).isPresent()) {
                dismounting.add(captive);
                convoy().dismount(captive);
                dismounting.remove(captive);
            }
            arrive(captive, actor);
        } else {
            bar(actor, text("convoy.already_led", name(captive), name(holder)));
        }
    }

    /** Поводок отпущен или оборвался: новая клетка, плен без клетки или таймер возврата. */
    private void arrive(TargetId captive, TargetId holder) {
        leashes.remove(captive);
        convoy().hideLeash(captive);
        Optional<Captive> found = captivity.find(captive);
        if (found.isEmpty()) {
            return;
        }
        if (captivity.moveToCell(captive)) {
            captivity.setCellChecksSuspended(captive, false);
            bar(holder, text("convoy.new_cell", name(captive)));
        } else if (found.get().cell().isEmpty()) {
            captivity.setCellChecksSuspended(captive, false);
            bar(holder, text("convoy.unleashed", name(captive)));
        } else {
            long seconds = Math.max(1, context.config().getInt("convoy.return_seconds", 60));
            returnDeadlines.put(captive, context.clock().millis() + seconds * 1000);
            context.platform().feedback().message(holder, text("convoy.return_timer", name(captive), seconds));
        }
    }

    private void tick() {
        ticks++;
        if (ticks % PULL_INTERVAL_TICKS == 0) {
            pull();
        }
        if (ticks % TIMER_INTERVAL_TICKS == 0) {
            checkDeadlines();
        }
    }

    private void pull() {
        double slack = context.config().getDouble("convoy.slack_distance", 3);
        double breakAt = context.config().getDouble("convoy.break_distance", 10);
        double strength = context.config().getDouble("convoy.pull_strength", 0.15);
        for (Map.Entry<TargetId, TargetId> leash : List.copyOf(leashes.entrySet())) {
            TargetId captive = leash.getKey();
            Optional<TargetState> captiveState = context.platform().targets().state(captive);
            Optional<TargetState> holderState = context.platform().targets().state(leash.getValue());
            if (captiveState.isEmpty() || holderState.isEmpty()
                    || !captiveState.get().location().worldId().equals(holderState.get().location().worldId())) {
                snap(captive, leash.getValue());
                continue;
            }
            if (convoy().vehicleOf(captive).isPresent()) {
                continue;
            }
            Location from = captiveState.get().location();
            Location to = holderState.get().location();
            double dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > breakAt) {
                snap(captive, leash.getValue());
            } else if (distance > slack) {
                double horizontal = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
                double speed = Math.min(0.6, (distance - slack) * strength);
                convoy().push(captive, dx / horizontal * speed, Math.max(-0.2, Math.min(0.42, dy * 0.1)),
                        dz / horizontal * speed);
            }
        }
    }

    private void snap(TargetId captive, TargetId holder) {
        bar(holder, text("convoy.snapped", name(captive)));
        arrive(captive, holder);
    }

    private void checkDeadlines() {
        long now = context.clock().millis();
        for (Map.Entry<TargetId, Long> deadline : List.copyOf(returnDeadlines.entrySet())) {
            TargetId captive = deadline.getKey();
            if (captivity.moveToCell(captive)) {
                returnDeadlines.remove(captive);
                captivity.setCellChecksSuspended(captive, false);
                captivity.find(captive).flatMap(Captive::captor)
                        .ifPresent(captor -> bar(captor, text("convoy.returned", name(captive))));
            } else if (now > deadline.getValue()) {
                returnDeadlines.remove(captive);
                captivity.setCellChecksSuspended(captive, false);
                captivity.release(captive, ReleaseReason.LEFT_CELL);
            }
        }
    }

    private void forget(TargetId captive) {
        if (leashes.remove(captive) != null) {
            convoy().hideLeash(captive);
        }
        returnDeadlines.remove(captive);
    }

    private Optional<TargetId> ledBy(TargetId holder) {
        return leashes.entrySet().stream().filter(entry -> entry.getValue().equals(holder))
                .map(Map.Entry::getKey).findFirst();
    }

    private List<String> leashItems() {
        return context.config().has("convoy.leash_items")
                ? context.config().getStringList("convoy.leash_items")
                : List.of("minecraft:lead");
    }

    private Convoy convoy() {
        return context.platform().convoy();
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
}
