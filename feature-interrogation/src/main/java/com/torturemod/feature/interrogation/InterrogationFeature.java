package com.torturemod.feature.interrogation;

import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.torture.CaptiveMenuExtensions;
import com.torturemod.api.torture.MenuButton;

import java.util.List;
import java.util.Optional;

/**
 * Допрос пленных игроков: кнопка в меню пленного выдаёт палачу то, что пленный готов рассказать.
 * Нужны плен, сопротивление и меню пленного из фичи пыток.
 */
public final class InterrogationFeature implements Feature {
    private Scheduler.Task tickTask;

    @Override
    public String id() {
        return "interrogation";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = require(context, CaptivityService.class);
        ResistanceService resistance = require(context, ResistanceService.class);
        CaptiveMenuExtensions menu = require(context, CaptiveMenuExtensions.class);
        AllyTracker allies = new AllyTracker(context, captivity);
        Interrogator interrogator = new Interrogator(context, resistance, allies);
        tickTask = context.platform().scheduler().everyTick(allies::tick);

        menu.add((viewer, captive) -> {
            if (captivity.find(captive).filter(found -> found.kind() == TargetKind.PLAYER).isEmpty()) {
                return Optional.empty();
            }
            Optional<com.torturemod.api.model.Location> where = context.platform().targets().state(captive)
                    .map(com.torturemod.api.model.TargetState::location);
            if (where.isEmpty() || !captivity.allows(viewer, AccessRight.INTERROGATE, Optional.of(captive), where.get())) {
                return Optional.empty();
            }
            List<String> lore = interrogator.unlocked(captive).entrySet().stream()
                    .map(entry -> context.messages().text(entry.getValue() ? "interrogation.lore.open" : "interrogation.lore.locked",
                            context.messages().text("interrogation.source." + entry.getKey())))
                    .toList();
            return Optional.of(new MenuButton("minecraft:writable_book", context.messages().text("interrogation.button"),
                    lore, () -> {
                        context.platform().menus().close(viewer);
                        interrogator.interrogate(viewer, captive);
                    }));
        });
    }

    @Override
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    private static <T> T require(FeatureContext context, Class<T> type) {
        return context.services().find(type).orElseThrow(() -> new IllegalStateException(
                "Interrogation needs " + type.getSimpleName() + ": start its feature first"));
    }
}
