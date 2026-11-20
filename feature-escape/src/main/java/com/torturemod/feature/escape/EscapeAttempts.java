package com.torturemod.feature.escape;

import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.resistance.ResistanceView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * Попытки побега. Игроку раз в несколько минут приходит серия прыжков и приседаний: выполнил вовремя —
 * бросается шанс, который тем выше, чем больше осталось сопротивления. Мобы пытаются с чистым шансом.
 * Сломленный сам не сбегает. Неудача отодвигает следующую попытку.
 */
final class EscapeAttempts {
    private static final int CHECK_INTERVAL_TICKS = 5;

    private static final class State {
        long nextAttemptAt;
        List<GameEvent.Input> sequence;
        int index;
        long deadline;

        State(long nextAttemptAt) {
            this.nextAttemptAt = nextAttemptAt;
        }
    }

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final ResistanceService resistance;
    private final DoubleSupplier random;
    /** С наручниками попытка означает выскользнуть из них: плен остаётся. */
    private final Optional<CuffsService> cuffs;
    private final Map<TargetId, State> states = new HashMap<>();
    private long ticks;

    EscapeAttempts(FeatureContext context, CaptivityService captivity, ResistanceService resistance,
                   DoubleSupplier random, Optional<CuffsService> cuffs) {
        this.cuffs = cuffs;
        this.context = context;
        this.captivity = captivity;
        this.resistance = resistance;
        this.random = random;
    }

    void schedule(TargetId captive) {
        states.put(captive, new State(now() + intervalMillis()));
    }

    void forget(TargetId captive) {
        states.remove(captive);
    }

    void tick() {
        if (++ticks % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        long now = now();
        Set<TargetId> online = context.platform().players().online().stream()
                .map(TargetState::id).collect(Collectors.toSet());
        for (Captive captive : List.copyOf(captivity.captives())) {
            TargetId id = captive.target();
            State state = states.computeIfAbsent(id, key -> new State(now + intervalMillis()));
            if (state.sequence != null) {
                if (now > state.deadline) {
                    fail(id, state, "escape.timeout");
                }
                continue;
            }
            if (now < state.nextAttemptAt) {
                continue;
            }
            // С наручниками игрок пытается выскользнуть из них; без наручников ему не из чего вырываться.
            boolean uncuffedPlayer = captive.kind() == TargetKind.PLAYER && cuffs.isPresent() && !cuffs.get().isCuffed(id);
            if (resistance.isBroken(id) || uncuffedPlayer) {
                state.nextAttemptAt = now + intervalMillis();
                continue;
            }
            if (captive.kind() == TargetKind.PLAYER) {
                if (online.contains(id)) {
                    startSequence(id, state, now);
                }
            } else if (context.platform().targets().state(id).isPresent()) {
                roll(id, state);
            }
        }
    }

    void onInput(GameEvent.InputPressed event) {
        State state = states.get(event.player());
        if (state == null || state.sequence == null) {
            return;
        }
        if (event.input() != state.sequence.get(state.index)) {
            fail(event.player(), state, "escape.missed");
            return;
        }
        state.index++;
        if (state.index < state.sequence.size()) {
            context.platform().feedback().actionBar(event.player(), progress(state));
            return;
        }
        state.sequence = null;
        roll(event.player(), state);
    }

    private void startSequence(TargetId player, State state, long now) {
        int length = Math.max(1, context.config().getInt("escape.sequence_length", 3));
        List<GameEvent.Input> sequence = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            sequence.add(random.getAsDouble() < 0.5 ? GameEvent.Input.JUMP : GameEvent.Input.SNEAK);
        }
        state.sequence = sequence;
        state.index = 0;
        state.deadline = now + (long) (context.config().getDouble("escape.sequence_seconds", 8) * 1000);
        String steps = sequence.stream().map(this::inputName).collect(Collectors.joining(" → "));
        context.platform().feedback().message(player, text("escape.sequence", steps));
        context.platform().feedback().actionBar(player, progress(state));
    }

    private void roll(TargetId captive, State state) {
        double chance = context.config().getDouble("escape.max_chance_percent", 25) / 100 * resistanceFraction(captive);
        if (random.getAsDouble() < chance) {
            boolean player = captivity.find(captive).filter(found -> found.kind() == TargetKind.PLAYER).isPresent();
            if (player && cuffs.isPresent()) {
                cuffs.get().uncuff(captive);
                state.nextAttemptAt = now() + intervalMillis();
                if (player) {
                    context.platform().feedback().message(captive, text("escape.slipped"));
                }
                return;
            }
            if (player) {
                context.platform().feedback().message(captive, text("escape.success"));
            }
            captivity.release(captive, ReleaseReason.ESCAPE);
        } else {
            fail(captive, state, "escape.failed");
        }
    }

    private void fail(TargetId captive, State state, String key) {
        state.sequence = null;
        long delay = (long) (intervalMillis() * Math.max(1, context.config().getDouble("escape.failure_multiplier", 2)));
        state.nextAttemptAt = now() + delay;
        if (captivity.find(captive).filter(found -> found.kind() == TargetKind.PLAYER).isPresent()) {
            context.platform().feedback().message(captive, text(key, delay / 1000));
        }
    }

    private double resistanceFraction(TargetId captive) {
        Optional<ResistanceView> view = resistance.view(captive);
        if (view.isEmpty()) {
            return 1;
        }
        return view.get().broken() ? 0 : view.get().value() / view.get().max();
    }

    /** Пройденные шаги отмечены, текущий в скобках. */
    private String progress(State state) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < state.sequence.size(); i++) {
            String name = inputName(state.sequence.get(i));
            parts.add(i < state.index ? "✔ " + name : i == state.index ? "[" + name + "]" : name);
        }
        return text("escape.progress", String.join(" → ", parts));
    }

    private String inputName(GameEvent.Input input) {
        return text("escape.input." + input.name());
    }

    private long intervalMillis() {
        return (long) (Math.max(1, context.config().getDouble("escape.attempt_interval_seconds", 180)) * 1000);
    }

    private long now() {
        return context.clock().millis();
    }

    private String text(String key, Object... args) {
        return context.messages().text(key, args);
    }
}
