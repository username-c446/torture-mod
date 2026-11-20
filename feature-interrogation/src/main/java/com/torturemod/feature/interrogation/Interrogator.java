package com.torturemod.feature.interrogation;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.InventoryKind;
import com.torturemod.api.model.ItemEntry;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.resistance.ResistanceView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Допрос. Каждый источник сведений открывается, когда сопротивление опустилось до своего порога.
 * Новые источники, например метки модов на карты, добавляются сюда же.
 */
final class Interrogator {
    private final FeatureContext context;
    private final ResistanceService resistance;
    private final AllyTracker allies;
    private final Map<String, Function<TargetId, List<String>>> sources = new LinkedHashMap<>();
    private final Map<String, Integer> defaultThresholds = new LinkedHashMap<>();

    Interrogator(FeatureContext context, ResistanceService resistance, AllyTracker allies) {
        this.context = context;
        this.resistance = resistance;
        this.allies = allies;
        source("allies", 50, this::allies);
        source("coordinates", 25, this::coordinates);
        source("inventory", 0, captive -> items(captive, InventoryKind.MAIN));
        source("ender_chest", 0, captive -> items(captive, InventoryKind.ENDER_CHEST));
    }

    private void source(String id, int threshold, Function<TargetId, List<String>> reveal) {
        sources.put(id, reveal);
        defaultThresholds.put(id, threshold);
    }

    /** Что пленный готов рассказать сейчас: источник → открыт ли. */
    Map<String, Boolean> unlocked(TargetId captive) {
        double percent = percent(captive);
        Map<String, Boolean> result = new LinkedHashMap<>();
        sources.keySet().forEach(id -> result.put(id, percent <= threshold(id)));
        return result;
    }

    void interrogate(TargetId interrogator, TargetId captive) {
        String name = context.platform().targets().state(captive).map(TargetState::name).orElse(captive.value().toString());
        List<String> lines = new ArrayList<>();
        lines.add(text("interrogation.header", name));
        unlocked(captive).forEach((id, open) -> {
            String title = text("interrogation.source." + id);
            if (!open) {
                lines.add(text("interrogation.locked", title, threshold(id)));
                return;
            }
            sources.get(id).apply(captive).forEach(line -> lines.add(title + ": " + line));
        });
        lines.forEach(line -> context.platform().feedback().message(interrogator, line));
    }

    private double percent(TargetId captive) {
        Optional<ResistanceView> view = resistance.view(captive);
        if (view.isEmpty()) {
            return 100;
        }
        return view.get().broken() ? 0 : view.get().value() / view.get().max() * 100;
    }

    private int threshold(String id) {
        return context.config().getInt("interrogation.sources." + id + ".threshold_percent", defaultThresholds.get(id));
    }

    private List<String> allies(TargetId captive) {
        List<String> lines = new ArrayList<>();
        context.platform().players().team(captive).ifPresent(team -> {
            List<String> mates = context.platform().players().teamMembers(team).stream()
                    .filter(member -> !member.equals(name(captive))).toList();
            lines.add(text("interrogation.allies.team", team, mates.isEmpty() ? "—" : String.join(", ", mates)));
        });
        List<String> near = allies.frequentCompanions(captive).stream().map(this::name).toList();
        if (!near.isEmpty()) {
            lines.add(text("interrogation.allies.near", String.join(", ", near)));
        }
        return lines.isEmpty() ? List.of(text("interrogation.allies.none")) : lines;
    }

    private List<String> coordinates(TargetId captive) {
        List<String> lines = new ArrayList<>();
        context.platform().players().respawnPoint(captive)
                .ifPresent(point -> lines.add(text("interrogation.coordinates.respawn", format(point))));
        context.platform().players().lastDeath(captive)
                .ifPresent(point -> lines.add(text("interrogation.coordinates.death", format(point))));
        return lines.isEmpty() ? List.of(text("interrogation.coordinates.none")) : lines;
    }

    private List<String> items(TargetId captive, InventoryKind kind) {
        Map<String, Integer> totals = new TreeMap<>();
        for (ItemEntry entry : context.platform().inventories().contents(captive, kind)) {
            totals.merge(entry.itemId(), entry.count(), Integer::sum);
        }
        if (totals.isEmpty()) {
            return List.of(text("interrogation.items.empty"));
        }
        List<String> parts = new ArrayList<>();
        totals.forEach((item, count) -> parts.add(item + " ×" + count));
        return List.of(String.join(", ", parts));
    }

    private String name(TargetId player) {
        return context.platform().targets().playerName(player).orElse(player.value().toString());
    }

    private static String format(BlockLocation point) {
        return point.worldId() + " " + point.x() + " " + point.y() + " " + point.z();
    }

    private String text(String key, Object... args) {
        return context.messages().text(key, args);
    }
}
