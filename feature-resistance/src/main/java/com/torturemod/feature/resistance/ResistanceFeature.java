package com.torturemod.feature.resistance;

import com.torturemod.api.captivity.CaptiveCaptured;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.feature.ConfigReloaded;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.resistance.ResistanceService;

import java.util.ArrayList;
import java.util.List;

/** Шкала сопротивления: привыкание, восстановление, слом. Нужен плен; предоставляет {@link ResistanceService}. */
public final class ResistanceFeature implements Feature {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private volatile ResistanceSettings settings;
    private Scheduler.Task tickTask;

    @Override
    public String id() {
        return "resistance";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = context.services().find(CaptivityService.class)
                .orElseThrow(() -> new IllegalStateException("Resistance needs the captivity feature started first"));
        settings = ResistanceSettings.from(context.config());
        ResistanceTracker tracker = new ResistanceTracker(context, captivity, () -> settings);
        context.services().provide(ResistanceService.class, tracker);
        subscriptions.add(context.bus().subscribe(ConfigReloaded.class,
                event -> settings = ResistanceSettings.from(context.config())));
        subscriptions.add(context.bus().subscribe(CaptiveCaptured.class, event -> tracker.onCaptured(event.captive())));
        subscriptions.add(context.bus().subscribe(CaptiveReleased.class,
                event -> tracker.forget(event.captive().target())));
        tickTask = context.platform().scheduler().everyTick(tracker::tick);
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (tickTask != null) {
            tickTask.cancel();
        }
    }
}
