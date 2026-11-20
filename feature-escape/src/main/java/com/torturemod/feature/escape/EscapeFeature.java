package com.torturemod.feature.escape;

import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.captivity.CaptiveCaptured;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.resistance.ResistanceService;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/** Побег: попытки пленного и освобождение помощником. Нужны плен и сопротивление. */
public final class EscapeFeature implements Feature {
    private final DoubleSupplier random;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private Scheduler.Task tickTask;

    public EscapeFeature() {
        this(new SplittableRandom()::nextDouble);
    }

    /** @param random случайные числа от 0 до 1; в тестах задаются заранее */
    public EscapeFeature(DoubleSupplier random) {
        this.random = random;
    }

    @Override
    public String id() {
        return "escape";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = require(context, CaptivityService.class);
        ResistanceService resistance = require(context, ResistanceService.class);
        EscapeAttempts attempts = new EscapeAttempts(context, captivity, resistance, random,
                context.services().find(CuffsService.class));
        Rescue rescue = new Rescue(context, captivity);
        EventBus bus = context.bus();
        subscriptions.add(bus.subscribe(CaptiveCaptured.class, event -> attempts.schedule(event.captive().target())));
        subscriptions.add(bus.subscribe(CaptiveReleased.class, event -> {
            attempts.forget(event.captive().target());
            rescue.forget(event.captive().target());
        }));
        subscriptions.add(bus.subscribe(GameEvent.InputPressed.class, attempts::onInput));
        subscriptions.add(bus.subscribe(GameEvent.EntityInteracted.class, rescue::onInteract));
        subscriptions.add(bus.subscribe(GameEvent.Damaged.class, rescue::onDamaged));
        tickTask = context.platform().scheduler().everyTick(() -> {
            attempts.tick();
            rescue.tick();
        });
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    private static <T> T require(FeatureContext context, Class<T> type) {
        return context.services().find(type).orElseThrow(() -> new IllegalStateException(
                "Escape needs " + type.getSimpleName() + ": start its feature first"));
    }
}
