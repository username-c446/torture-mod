package com.torturemod.feature.offline;

import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.resistance.ResistanceService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Выход пленного из игры: на его месте остаётся тело, за выход снимается сопротивление,
 * а после нескольких выходов он сразу сломлен. Нужны плен и сопротивление.
 * Число выходов хранится в памяти и обнуляется при перезапуске сервера.
 */
public final class OfflineFeature implements Feature {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private final Map<TargetId, Integer> exits = new HashMap<>();
    /** Что сообщить игроку при заходе: наказания пришлись на время, пока его не было. */
    private final Map<TargetId, String> pendingNotice = new HashMap<>();
    private FeatureContext context;

    @Override
    public String id() {
        return "offline";
    }

    @Override
    public void start(FeatureContext context) {
        this.context = context;
        CaptivityService captivity = require(CaptivityService.class);
        ResistanceService resistance = require(ResistanceService.class);

        if (bodiesEnabled() && config().getBoolean("offline.restore_bodies_after_restart", true)) {
            for (Captive captive : captivity.captives()) {
                if (captive.kind() == TargetKind.PLAYER
                        && context.platform().targets().state(captive.target()).isEmpty()) {
                    context.platform().bodies().spawn(captive.target());
                }
            }
        }

        subscriptions.add(context.bus().subscribe(GameEvent.PlayerLeft.class, event -> {
            TargetId player = event.player();
            if (!captivity.isCaptive(player)) {
                return;
            }
            int count = exits.merge(player, 1, Integer::sum);
            int breakAfter = config().getInt("offline.break_after_exits", 5);
            if (config().getBoolean("offline.penalties.resistance_loss", true)) {
                resistance.penalize(player, config().getDouble("offline.resistance_loss_percent", 10) / 100);
            }
            if (config().getBoolean("offline.penalties.break_on_repeat", true) && breakAfter > 0 && count >= breakAfter) {
                resistance.breakNow(player);
                pendingNotice.put(player, context.messages().text("offline.broken_by_exits", count));
            } else if (config().getBoolean("offline.penalties.resistance_loss", true)) {
                pendingNotice.put(player, context.messages().text("offline.penalty", count, breakAfter));
            }
            if (bodiesEnabled()) {
                context.platform().bodies().spawn(player);
            }
        }));
        subscriptions.add(context.bus().subscribe(GameEvent.PlayerJoined.class, event -> {
            if (context.platform().bodies().exists(event.player())) {
                context.platform().bodies().restore(event.player());
            }
            String notice = pendingNotice.remove(event.player());
            if (notice != null && captivity.isCaptive(event.player())) {
                context.platform().feedback().message(event.player(), notice);
            }
        }));
        subscriptions.add(context.bus().subscribe(CaptiveReleased.class, event -> {
            TargetId player = event.captive().target();
            exits.remove(player);
            pendingNotice.remove(player);
            if (context.platform().bodies().exists(player)) {
                context.platform().bodies().remove(player);
            }
        }));
    }

    /** Тела убираются с сохранением состояния, чтобы при заходе игрок оказался там же. */
    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (context == null) {
            return;
        }
        context.services().find(CaptivityService.class).ifPresent(captivity -> captivity.captives().forEach(captive -> {
            if (context.platform().bodies().exists(captive.target())) {
                context.platform().bodies().remove(captive.target());
            }
        }));
    }

    private boolean bodiesEnabled() {
        return config().getBoolean("offline.penalties.body", true);
    }

    private ConfigView config() {
        return context.config();
    }

    private <T> T require(Class<T> type) {
        return context.services().find(type).orElseThrow(() -> new IllegalStateException(
                "Offline needs " + type.getSimpleName() + ": start its feature first"));
    }
}
