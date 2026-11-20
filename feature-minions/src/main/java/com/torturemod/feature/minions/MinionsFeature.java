package com.torturemod.feature.minions;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.prison.EscapeHelped;
import com.torturemod.api.prison.PrisonCouriers;
import com.torturemod.api.prison.PrisonEscaped;
import com.torturemod.api.prison.PrisonGuards;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.api.resistance.CaptiveBroken;
import com.torturemod.api.resistance.ResistanceService;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/**
 * Сломленные мобы на службе тюрьмы: контроль разума оком Эндера, охрана, курьеры, жители со скидкой
 * и фермеры. Нужны плен, сопротивление и тюрьма.
 */
public final class MinionsFeature implements Feature {
    private final DoubleSupplier random;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private Scheduler.Task tickTask;

    public MinionsFeature() {
        this(new SplittableRandom()::nextDouble);
    }

    /** @param random случайные числа от 0 до 1 для обхода поста; в тестах задаются заранее */
    public MinionsFeature(DoubleSupplier random) {
        this.random = random;
    }

    @Override
    public String id() {
        return "minions";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = require(context, CaptivityService.class);
        ResistanceService resistance = require(context, ResistanceService.class);
        PrisonService prisons = require(context, PrisonService.class);
        Army army = new Army(context, captivity, resistance, prisons, random);
        army.load();
        context.services().provide(PrisonGuards.class, army::anyGuardSees);
        context.services().provide(PrisonCouriers.class, army::deliver);
        prisons.addConsoleSection(army::consoleButton);
        EventBus bus = context.bus();
        subscriptions.add(bus.subscribe(GameEvent.EntityInteracted.class, army::onInteract));
        subscriptions.add(bus.subscribe(GameEvent.Damaged.class, army::onDamaged));
        subscriptions.add(bus.subscribe(GameEvent.Died.class, event -> army.onDied(event.target())));
        subscriptions.add(bus.subscribe(CaptiveBroken.class, event -> army.onBroken(event.captive())));
        subscriptions.add(bus.subscribe(PrisonEscaped.class, army::onEscaped));
        subscriptions.add(bus.subscribe(EscapeHelped.class, army::onHelped));
        tickTask = context.platform().scheduler().everyTick(army::tick);
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
                "Minions need " + type.getSimpleName() + ": start its feature first"));
    }
}
