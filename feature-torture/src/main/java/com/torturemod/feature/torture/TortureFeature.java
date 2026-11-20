package com.torturemod.feature.torture;

import java.util.Optional;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.items.Classification;
import com.torturemod.api.items.ItemClassifier;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.resistance.CaptiveBroken;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.torture.CaptiveMenuExtensions;

import java.util.ArrayList;
import java.util.List;

/**
 * Пытки: ПКМ предметом по пленному, Shift + ПКМ открывает меню пленного.
 * Нужны плен, предметы и сопротивление.
 */
public final class TortureFeature implements Feature {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    @Override
    public String id() {
        return "torture";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = require(context, CaptivityService.class);
        ResistanceService resistance = require(context, ResistanceService.class);
        ItemClassifier classifier = require(context, ItemClassifier.class);
        // Настройки типа читаются из текущего снимка конфига, поэтому перезагрузка применяется сразу.
        Torturer torturer = new Torturer(context, resistance, type -> TypeSettings.from(context.config(), type));
        CaptiveMenu menu = new CaptiveMenu(context, captivity, resistance, classifier, torturer,
                type -> TypeSettings.from(context.config(), type));
        context.services().provide(CaptiveMenuExtensions.class, menu::addContributor);

        subscriptions.add(context.bus().subscribe(GameEvent.EntityInteracted.class, event -> {
            if (event.cancellation().isCancelled() || !captivity.isCaptive(event.target())
                    || captivity.isCaptive(event.actor())) {
                return;
            }
            if (event.sneaking()) {
                event.cancellation().cancel();
                if (allowed(context, captivity, event.actor(), event.target(), AccessRight.TORTURE)) {
                    menu.open(event.actor(), event.target());
                }
                return;
            }
            if (event.itemId().isEmpty()) {
                return;
            }
            Classification item = classifier.classify(event.itemId().get());
            if (item.types().isEmpty()) {
                return;
            }
            // Пытка вместо обычного действия предмета: ведро не доит корову, поводок не привязывает.
            event.cancellation().cancel();
            if (!allowed(context, captivity, event.actor(), event.target(), AccessRight.TORTURE)) {
                return;
            }
            torturer.torture(event.actor(), event.target(), item.types(), item.strength(),
                    () -> context.platform().items().findInHands(event.actor(), item.itemId())
                            .ifPresent(slot -> context.platform().items().spend(event.actor(), slot)));
        }));
        subscriptions.add(context.bus().subscribe(CaptiveReleased.class,
                event -> torturer.forget(event.captive().target())));
        subscriptions.add(context.bus().subscribe(CaptiveBroken.class, event ->
                captivity.find(event.captive()).filter(captive -> captive.kind() == TargetKind.PLAYER)
                        .ifPresent(captive -> context.platform().feedback()
                                .message(captive.target(), context.messages().text("torture.you_are_broken")))));
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
    }

    /** Право на пленного, например в чужой тюрьме; при отказе палач видит подсказку. */
    static boolean allowed(FeatureContext context, CaptivityService captivity, TargetId actor, TargetId captive,
                           AccessRight right) {
        Optional<TargetState> target = context.platform().targets().state(captive);
        if (target.isEmpty()) {
            return false;
        }
        if (captivity.allows(actor, right, Optional.of(captive), target.get().location())) {
            return true;
        }
        context.platform().feedback().actionBar(actor, context.messages().text("torture.no_right"));
        return false;
    }

    private static <T> T require(FeatureContext context, Class<T> type) {
        return context.services().find(type).orElseThrow(() -> new IllegalStateException(
                "Torture needs " + type.getSimpleName() + ": start its feature first"));
    }
}
