package com.torturemod.feature.captivity;

import java.util.ArrayList;
import com.torturemod.api.model.Location;
import com.torturemod.api.captivity.Containment;
import com.torturemod.api.captivity.CaptiveAction;
import com.torturemod.api.captivity.ActionRule;
import com.torturemod.api.captivity.AccessRule;
import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.CaptiveCaptured;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.CaptureFailure;
import com.torturemod.api.captivity.CaptureMethod;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.port.RegionPolicy;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Кто в плену. Состояние живёт в памяти основного потока, запись в хранилище идёт в фоне. */
final class CaptivityRegistry implements CaptivityService {
    /** Начальное сопротивление; шкалу ведёт фича сопротивления. */
    private static final double INITIAL_RESISTANCE = 100;

    private final FeatureContext context;
    private final Supplier<CaptivitySettings> settings;
    private final Map<TargetId, CaptivityRecord> records = new LinkedHashMap<>();
    private final Map<TargetId, Long> immuneUntil = new HashMap<>();
    private final java.util.Set<TargetId> suspended = new java.util.HashSet<>();
    private CellWatcher watcher;
    private final List<PrioritizedRule> actionRules = new ArrayList<>();
    private final List<Containment> containments = new ArrayList<>();
    private final List<AccessRule> accessRules = new ArrayList<>();

    private record PrioritizedRule(int priority, ActionRule rule) {
    }

    CaptivityRegistry(FeatureContext context, Supplier<CaptivitySettings> settings) {
        this.context = context;
        this.settings = settings;
    }

    void attach(CellWatcher watcher) {
        this.watcher = watcher;
    }

    boolean isSuspended(TargetId target) {
        return suspended.contains(target);
    }

    @Override
    public void setCellChecksSuspended(TargetId target, boolean suspend) {
        if (suspend && records.containsKey(target)) {
            suspended.add(target);
        } else {
            suspended.remove(target);
        }
    }

    @Override
    public boolean isCellChecksSuspended(TargetId target) {
        return suspended.contains(target);
    }

    @Override
    public void addActionRule(int priority, ActionRule rule) {
        actionRules.add(new PrioritizedRule(priority, rule));
        actionRules.sort(java.util.Comparator.comparingInt(PrioritizedRule::priority));
    }

    @Override
    public boolean mayAct(TargetId captive, CaptiveAction action, BlockLocation where) {
        Optional<Captive> found = find(captive);
        if (found.isEmpty()) {
            return true;
        }
        for (PrioritizedRule entry : actionRules) {
            Optional<Boolean> decision = entry.rule().decide(found.get(), action, where);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        return false;
    }

    @Override
    public void addContainment(Containment containment) {
        containments.add(containment);
    }

    /** Пленный под охраной места содержания: клетка его не держит и не отпускает. */
    boolean isClaimed(TargetId target) {
        Optional<Captive> captive = find(target);
        return captive.isPresent() && containments.stream().anyMatch(c -> c.claims(captive.get()));
    }

    @Override
    public void addAccessRule(AccessRule rule) {
        accessRules.add(rule);
    }

    @Override
    public boolean allows(TargetId actor, AccessRight right, Optional<TargetId> captive, Location where) {
        if (context.platform().permissions().has(actor, Permissions.ADMIN)) {
            return true;
        }
        Optional<Captive> found = captive.flatMap(this::find);
        for (AccessRule rule : accessRules) {
            Optional<Boolean> decision = rule.decide(actor, right, found, where);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        return true;
    }

    @Override
    public boolean moveToCell(TargetId target) {
        CaptivityRecord record = records.get(target);
        Optional<TargetState> state = context.platform().targets().state(target);
        if (record == null || state.isEmpty()) {
            return false;
        }
        BlockLocation anchor = BlockLocation.of(state.get().location());
        CageScanner.Result cell = CageScanner.scan(context.platform().blocks(), anchor, settings.get());
        if (!cell.isCell()) {
            // Без клетки пленного может удержать место содержания, например тюрьма.
            Captive captive = toCaptive(record);
            return containments.stream().anyMatch(c -> c.holds(captive, state.get().location()));
        }
        CaptivityRecord updated = new CaptivityRecord(record.target(), record.kind(), record.captor(), record.resistance(),
                record.broken(), record.capturedAtEpochMillis(), record.location(), Optional.of(anchor));
        records.put(target, updated);
        context.storage().save(updated).exceptionally(error -> logFailure("save", target, error));
        if (watcher != null) {
            watcher.remember(target, cell.interior());
        }
        return true;
    }

    void load() {
        for (CaptivityRecord record : context.storage().loadAll().join()) {
            records.put(record.target(), record);
        }
    }

    Optional<CaptivityRecord> record(TargetId target) {
        return Optional.ofNullable(records.get(target));
    }

    List<CaptivityRecord> records() {
        return List.copyOf(records.values());
    }

    boolean isCaptivePlayer(TargetId target) {
        CaptivityRecord record = records.get(target);
        return record != null && record.kind() == TargetKind.PLAYER;
    }

    @Override
    public Optional<Captive> find(TargetId target) {
        return record(target).map(CaptivityRegistry::toCaptive);
    }

    @Override
    public boolean isCaptive(TargetId target) {
        return records.containsKey(target);
    }

    @Override
    public Collection<Captive> captives() {
        return records.values().stream().map(CaptivityRegistry::toCaptive).toList();
    }

    @Override
    public Optional<CaptureFailure> captureWithoutCell(TargetId target, Optional<TargetId> captor) {
        Optional<TargetState> state = context.platform().targets().state(target);
        if (state.isEmpty()) {
            return Optional.of(CaptureFailure.NOT_FOUND);
        }
        Optional<CaptureFailure> failure = check(captor, state.get(), true);
        if (failure.isEmpty()) {
            add(state.get(), captor, Optional.empty(), CaptureMethod.COMMAND);
        }
        return failure;
    }

    /** @param byAdmin администратор не связан защитой от повторного захвата и приватами */
    Optional<CaptureFailure> check(Optional<TargetId> captor, TargetState target, boolean byAdmin) {
        if (captor.isPresent() && captor.get().equals(target.id())) {
            return Optional.of(CaptureFailure.SELF);
        }
        if (records.containsKey(target.id())) {
            return Optional.of(CaptureFailure.ALREADY_CAPTIVE);
        }
        if (target.kind() == TargetKind.PLAYER
                && context.platform().permissions().has(target.id(), Permissions.IMMUNE)) {
            return Optional.of(CaptureFailure.IMMUNE);
        }
        if (target.kind() == TargetKind.MOB && settings.get().blacklist().contains(target.typeId())) {
            return Optional.of(CaptureFailure.BLACKLISTED);
        }
        if (byAdmin) {
            return Optional.empty();
        }
        Long until = immuneUntil.get(target.id());
        if (until != null && context.clock().millis() < until) {
            return Optional.of(CaptureFailure.RECENTLY_RELEASED);
        }
        if (captor.isPresent() && !context.platform().regionPolicy()
                .allows(captor.get(), target.location(), RegionPolicy.Action.CAPTURE)) {
            return Optional.of(CaptureFailure.REGION);
        }
        if (captor.isPresent() && !allows(captor.get(), AccessRight.CAPTURE, Optional.empty(), target.location())) {
            return Optional.of(CaptureFailure.NOT_ALLOWED);
        }
        return Optional.empty();
    }

    Captive add(TargetState target, Optional<TargetId> captor, Optional<BlockLocation> cell, CaptureMethod method) {
        CaptivityRecord record = new CaptivityRecord(target.id(), target.kind(), captor, INITIAL_RESISTANCE, false,
                context.clock().millis(), target.location(), cell);
        records.put(target.id(), record);
        immuneUntil.remove(target.id());
        context.storage().save(record).exceptionally(error -> logFailure("save", target.id(), error));
        if (target.kind() == TargetKind.MOB) {
            context.platform().targets().setPersistent(target.id());
        } else {
            context.platform().feedback().message(target.id(), context.messages().text("captivity.you_are_captured"));
        }
        captor.ifPresent(id -> context.platform().feedback()
                .message(id, context.messages().text("captivity.you_captured", target.name())));
        Captive captive = toCaptive(record);
        context.bus().publish(new CaptiveCaptured(captive, method));
        return captive;
    }

    @Override
    public void updateResistance(TargetId target, double resistance, boolean broken) {
        CaptivityRecord record = records.get(target);
        if (record == null || record.resistance() == resistance && record.broken() == broken) {
            return;
        }
        CaptivityRecord updated = new CaptivityRecord(record.target(), record.kind(), record.captor(), resistance,
                broken, record.capturedAtEpochMillis(), record.location(), record.cell());
        records.put(target, updated);
        context.storage().save(updated).exceptionally(error -> logFailure("save", target, error));
    }

    @Override
    public boolean release(TargetId target, ReleaseReason reason) {
        CaptivityRecord record = records.remove(target);
        if (record == null) {
            return false;
        }
        suspended.remove(target);
        immuneUntil.put(target, context.clock().millis() + settings.get().recaptureImmunityMillis());
        context.storage().delete(target).exceptionally(error -> logFailure("delete", target, error));
        if (record.kind() == TargetKind.PLAYER) {
            context.platform().feedback().message(target, context.messages().text("captivity.you_are_released"));
        }
        context.bus().publish(new CaptiveReleased(toCaptive(record), reason));
        return true;
    }

    String displayName(TargetId target) {
        return context.platform().targets().state(target).map(TargetState::name)
                .or(() -> context.platform().targets().playerName(target))
                .orElse(target.value().toString());
    }

    private Void logFailure(String operation, TargetId target, Throwable error) {
        context.platform().log().error("Captivity " + operation + " failed for " + target.value(), error);
        return null;
    }

    private static Captive toCaptive(CaptivityRecord record) {
        return new Captive(record.target(), record.kind(), record.captor(), record.cell(),
                record.capturedAtEpochMillis(), record.resistance(), record.broken());
    }
}
