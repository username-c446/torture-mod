package com.torturemod.feature.captivity;

import com.torturemod.api.captivity.CaptiveAction;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.ConfigReloaded;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.port.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Плен, клетка, срабатывание, ограничения пленного. Предоставляет {@link CaptivityService}. */
public final class CaptivityFeature implements Feature {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private Scheduler.Task tickTask;
    private volatile CaptivitySettings settings;

    @Override
    public String id() {
        return "captivity";
    }

    @Override
    public void start(FeatureContext context) {
        settings = CaptivitySettings.from(context.config());
        CaptivityRegistry registry = new CaptivityRegistry(context, () -> settings);
        registry.load();
        CellWatcher watcher = new CellWatcher(context, registry, () -> settings);
        registry.attach(watcher);
        Restrictions restrictions = new Restrictions(context, registry, () -> settings);
        CaptureHandler capture = new CaptureHandler(context, registry, watcher, () -> settings);
        context.services().provide(CaptivityService.class, registry);

        // Ограничения подписаны раньше захвата: действие пленного отменяется до того, как его увидит захват.
        EventBus bus = context.bus();
        on(bus, ConfigReloaded.class, event -> settings = CaptivitySettings.from(context.config()));
        on(bus, GameEvent.BlockBroken.class, event -> {
            restrictions.blockAction(event.actor(), CaptiveAction.BREAK, event.block(), event.cancellation());
            if (!event.cancellation().isCancelled()) {
                watcher.onBlockChanged(event.block());
            }
        });
        on(bus, GameEvent.BlockPlaced.class,
                event -> restrictions.blockAction(event.actor(), CaptiveAction.PLACE, event.block(), event.cancellation()));
        on(bus, GameEvent.EntityInteracted.class, event -> {
            restrictions.blockAction(event.actor(), CaptiveAction.USE_ENTITY, event.cancellation());
            capture.onEntityInteracted(event);
        });
        on(bus, GameEvent.BlockInteracted.class, event -> {
            restrictions.blockAction(event.actor(), CaptiveAction.USE_BLOCK, event.block(), event.cancellation());
            capture.onBlockInteracted(event);
        });
        on(bus, GameEvent.ItemUsed.class, event -> restrictions.blockAction(event.actor(),
                context.platform().items().isFood(event.itemId()) ? CaptiveAction.EAT : CaptiveAction.USE_ITEM,
                event.cancellation()));
        on(bus, GameEvent.ItemDropped.class,
                event -> restrictions.blockAction(event.actor(), CaptiveAction.DROP, event.cancellation()));
        on(bus, GameEvent.AttackAttempted.class,
                event -> restrictions.blockAction(event.actor(), CaptiveAction.ATTACK, event.cancellation()));
        on(bus, GameEvent.ChatSent.class, restrictions::onChat);
        on(bus, GameEvent.CommandIssued.class, restrictions::onCommand);
        on(bus, GameEvent.Died.class, event -> registry.release(event.target(), ReleaseReason.DEATH));
        on(bus, CaptiveReleased.class, event -> watcher.forget(event.captive().target()));

        tickTask = context.platform().scheduler().everyTick(watcher::tick);
        new CaptivityCommands(context, registry).register();
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private <T> void on(EventBus bus, Class<T> type, Consumer<? super T> listener) {
        subscriptions.add(bus.subscribe(type, listener));
    }
}
