package com.torturemod.feature.resistance;

import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.items.TortureTypes;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.resistance.CaptiveBroken;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.resistance.ResistanceView;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Живая шкала пленных. Значения держатся в памяти и сохраняются через плен пачками, чтобы не писать
 * в базу каждую секунду восстановления. Привыкание не сохраняется: после перезапуска оно сбрасывается.
 */
final class ResistanceTracker implements ResistanceService {
    private static final int RECOVERY_INTERVAL_TICKS = 20;
    private static final int FLUSH_INTERVAL_TICKS = 100;

    private static final class State {
        final double max;
        final String typeId;
        double value;
        boolean broken;
        long lastTortureMillis;
        boolean dirty;
        double recoveryMultiplier = 1;
        long boostUntil;
        /** Тип пытки → действенность сразу после последнего применения и время применения. */
        final Map<String, double[]> habituation = new HashMap<>();

        State(double max, String typeId, double value, boolean broken) {
            this.max = max;
            this.typeId = typeId;
            this.value = value;
            this.broken = broken;
        }
    }

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final Supplier<ResistanceSettings> settings;
    private final Map<TargetId, State> states = new HashMap<>();
    private long ticks;

    ResistanceTracker(FeatureContext context, CaptivityService captivity, Supplier<ResistanceSettings> settings) {
        this.context = context;
        this.captivity = captivity;
        this.settings = settings;
    }

    /** Новый пленный начинает с полной шкалой своего профиля. */
    void onCaptured(Captive captive) {
        Optional<TargetState> target = context.platform().targets().state(captive.target());
        String typeId = target.map(TargetState::typeId).orElse(null);
        double max = settings.get().maxFor(captive.kind(), typeId);
        State state = new State(max, typeId, max, false);
        state.dirty = true;
        states.put(captive.target(), state);
    }

    void forget(TargetId captive) {
        states.remove(captive);
    }

    void tick() {
        ticks++;
        if (ticks % RECOVERY_INTERVAL_TICKS == 0) {
            recover();
        }
        if (ticks % FLUSH_INTERVAL_TICKS == 0) {
            states.forEach(this::flushIfDirty);
        }
    }

    @Override
    public Optional<ResistanceView> view(TargetId captive) {
        State state = state(captive);
        if (state == null) {
            return Optional.empty();
        }
        long now = context.clock().millis();
        Map<String, Double> effectiveness = new LinkedHashMap<>();
        for (String type : TortureTypes.ALL) {
            effectiveness.put(type, effectiveness(state, type, now));
        }
        state.habituation.keySet().forEach(type -> effectiveness.putIfAbsent(type, effectiveness(state, type, now)));
        return Optional.of(new ResistanceView(state.value, state.max, state.broken, effectiveness));
    }

    @Override
    public double weaken(TargetId captive, String tortureType, double baseDrop) {
        State state = state(captive);
        if (state == null || state.broken) {
            return 0;
        }
        ResistanceSettings current = settings.get();
        long now = context.clock().millis();
        double effectiveness = effectiveness(state, tortureType, now);
        double drop = Math.min(state.value,
                baseDrop * effectiveness * current.vulnerability(state.typeId, tortureType));
        state.value -= drop;
        state.habituation.put(tortureType, new double[]{
                Math.max(current.habituationMinimum(), effectiveness * current.habituationDecay()), now});
        state.lastTortureMillis = now;
        state.dirty = true;
        breakIfEmpty(captive, state);
        return drop;
    }

    @Override
    public void penalize(TargetId captive, double fractionOfMax) {
        State state = state(captive);
        if (state == null || state.broken) {
            return;
        }
        state.value = Math.max(0, state.value - state.max * Math.max(0, fractionOfMax));
        state.dirty = true;
        flushIfDirty(captive, state);
        breakIfEmpty(captive, state);
    }

    @Override
    public void breakNow(TargetId captive) {
        State state = state(captive);
        if (state == null || state.broken) {
            return;
        }
        state.value = 0;
        state.dirty = true;
        breakIfEmpty(captive, state);
    }

    private void breakIfEmpty(TargetId captive, State state) {
        if (state.value <= 0 && !state.broken) {
            state.value = 0;
            state.broken = true;
            flushIfDirty(captive, state);
            context.bus().publish(new CaptiveBroken(captive));
        }
    }

    @Override
    public void boostRecovery(TargetId captive, double multiplier, long durationMillis) {
        State state = state(captive);
        if (state != null) {
            state.recoveryMultiplier = Math.max(0, multiplier);
            state.boostUntil = context.clock().millis() + Math.max(0, durationMillis);
        }
    }

    @Override
    public boolean isBroken(TargetId captive) {
        State state = state(captive);
        return state != null && state.broken;
    }

    private State state(TargetId captive) {
        State state = states.get(captive);
        if (state != null) {
            return state;
        }
        Optional<Captive> found = captivity.find(captive);
        if (found.isEmpty()) {
            return null;
        }
        String typeId = context.platform().targets().state(captive).map(TargetState::typeId).orElse(null);
        double max = settings.get().maxFor(found.get().kind(), typeId);
        State loaded = new State(max, typeId, Math.min(max, found.get().resistance()), found.get().broken());
        states.put(captive, loaded);
        return loaded;
    }

    private double effectiveness(State state, String type, long now) {
        double[] last = state.habituation.get(type);
        if (last == null) {
            return 1;
        }
        double recovered = (now - (long) last[1]) / 1000.0 * settings.get().habituationRecoveryPerSecond();
        return Math.min(1, last[0] + recovered);
    }

    /** Восстановление идёт, только пока пленный в сети или загружен: выход из игры не помогает. */
    private void recover() {
        ResistanceSettings current = settings.get();
        long now = context.clock().millis();
        double seconds = RECOVERY_INTERVAL_TICKS / 20.0;
        for (Captive captive : captivity.captives()) {
            State state = state(captive.target());
            if (state == null || state.broken || state.value >= state.max
                    || now - state.lastTortureMillis < current.recoveryDelayMillis()
                    || context.platform().targets().state(captive.target()).isEmpty()) {
                continue;
            }
            double multiplier = now < state.boostUntil ? state.recoveryMultiplier : 1;
            state.value = Math.min(state.max, state.value + current.recoveryPerSecond(state.max) * seconds * multiplier);
            state.dirty = true;
        }
    }

    private void flushIfDirty(TargetId captive, State state) {
        if (state.dirty) {
            captivity.updateResistance(captive, state.value, state.broken);
            state.dirty = false;
        }
    }
}
