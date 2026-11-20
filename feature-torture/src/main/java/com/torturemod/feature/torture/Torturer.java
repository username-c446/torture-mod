package com.torturemod.feature.torture;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.items.TortureTypes;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.port.RegionPolicy;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.resistance.ResistanceView;
import com.torturemod.api.torture.TortureApplied;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Действие пытки: перезарядки, воздействие, шкала, звук и частицы, показ шкалы. */
final class Torturer {
    private final FeatureContext context;
    private final ResistanceService resistance;
    private final Function<String, TypeSettings> settings;
    private final Map<TargetId, Map<String, Long>> readyAt = new HashMap<>();
    private final Map<String, Scheduler.Task> barTimers = new HashMap<>();

    Torturer(FeatureContext context, ResistanceService resistance, Function<String, TypeSettings> settings) {
        this.context = context;
        this.resistance = resistance;
        this.settings = settings;
    }

    /**
     * @param spend расходует предмет; вызывается один раз, если сработал хотя бы один тип
     * @return сработал ли хотя бы один тип
     */
    boolean torture(TargetId actor, TargetId captive, Set<String> types, double strength, Runnable spend) {
        Optional<TargetState> target = context.platform().targets().state(captive);
        if (target.isEmpty()) {
            return false;
        }
        if (!context.platform().permissions().has(actor, Permissions.USE)) {
            bar(actor, context.messages().text("command.no_permission"));
            return false;
        }
        if (!context.platform().regionPolicy().allows(actor, target.get().location(), RegionPolicy.Action.TORTURE)) {
            bar(actor, context.messages().text("torture.region"));
            return false;
        }
        long now = context.clock().millis();
        boolean wasBroken = resistance.isBroken(captive);
        List<String> applied = new ArrayList<>();
        List<String> immune = new ArrayList<>();
        long wait = Long.MAX_VALUE;
        Map<String, Long> cooldowns = readyAt.computeIfAbsent(captive, key -> new HashMap<>());
        for (String type : ordered(types)) {
            TortureType action = TortureType.BUILT_IN.get(type);
            if (action == null) {
                continue;
            }
            long ready = cooldowns.getOrDefault(type, 0L);
            if (now < ready) {
                wait = Math.min(wait, ready - now);
                continue;
            }
            TypeSettings typeSettings = settings.apply(type);
            cooldowns.put(type, now + typeSettings.cooldownMillis());
            if (!action.apply(context.platform(), captive, actor, strength, typeSettings)) {
                immune.add(type);
                continue;
            }
            double drop = resistance.weaken(captive, type, typeSettings.resistanceDrop() * strength);
            if (!typeSettings.sound().isBlank()) {
                context.platform().feedback().sound(target.get().location(), typeSettings.sound());
            }
            if (!typeSettings.particle().isBlank()) {
                context.platform().feedback().particles(target.get().location(), typeSettings.particle(), 12);
            }
            context.bus().publish(new TortureApplied(captive, actor, type, drop));
            applied.add(type);
        }
        if (!applied.isEmpty()) {
            spend.run();
            showResistance(actor, captive, target.get());
            if (!wasBroken && resistance.isBroken(captive)) {
                context.platform().feedback().message(actor, context.messages().text("torture.broken", target.get().name()));
            }
            return true;
        }
        if (!immune.isEmpty()) {
            bar(actor, context.messages().text("torture.immune", target.get().name(),
                    String.join(", ", immune.stream().map(this::typeName).toList())));
        } else if (wait != Long.MAX_VALUE) {
            bar(actor, context.messages().text("torture.cooldown", (wait + 999) / 1000));
        }
        return false;
    }

    long cooldownLeft(TargetId captive, String type) {
        long ready = readyAt.getOrDefault(captive, Map.of()).getOrDefault(type, 0L);
        return Math.max(0, ready - context.clock().millis());
    }

    void forget(TargetId captive) {
        readyAt.remove(captive);
    }

    String typeName(String type) {
        return context.messages().text("torture.type." + type);
    }

    /** Шкала видна палачу и пленному, пока идёт пытка, и гаснет через несколько секунд без действий. */
    private void showResistance(TargetId actor, TargetId captive, TargetState target) {
        Optional<ResistanceView> view = resistance.view(captive);
        if (view.isEmpty()) {
            return;
        }
        int percent = (int) Math.round(view.get().value() / view.get().max() * 100);
        String title = view.get().broken()
                ? context.messages().text("torture.bar_broken", target.name())
                : context.messages().text("torture.resistance", target.name(), percent);
        float progress = (float) (view.get().value() / view.get().max());
        String key = "torturemod:resistance:" + captive.value();
        List<TargetId> viewers = new ArrayList<>(List.of(actor));
        if (target.kind() == TargetKind.PLAYER) {
            viewers.add(captive);
        }
        int hideAfter = Math.max(20, context.config().getInt("torture.bar_seconds", 10) * 20);
        for (TargetId viewer : viewers) {
            context.platform().feedback().showBar(viewer, key, title, progress);
            String timerKey = viewer.value() + "|" + key;
            Scheduler.Task previous = barTimers.remove(timerKey);
            if (previous != null) {
                previous.cancel();
            }
            barTimers.put(timerKey, context.platform().scheduler().later(hideAfter, () -> {
                barTimers.remove(timerKey);
                context.platform().feedback().hideBar(viewer, key);
            }));
        }
        context.platform().feedback().actionBar(actor, title);
    }

    private void bar(TargetId actor, String text) {
        context.platform().feedback().actionBar(actor, text);
    }

    private static List<String> ordered(Set<String> types) {
        List<String> result = new ArrayList<>(TortureTypes.ALL.stream().filter(types::contains).toList());
        types.stream().filter(type -> !result.contains(type)).sorted().forEach(result::add);
        return result;
    }
}
