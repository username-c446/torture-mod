package com.torturemod.feature.torture;

import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.Captive;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.items.Classification;
import com.torturemod.api.items.ItemClassifier;
import com.torturemod.api.items.TortureTypes;
import com.torturemod.api.model.InventoryKind;
import com.torturemod.api.model.ItemEntry;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Menus;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.resistance.ResistanceView;
import com.torturemod.api.torture.CaptiveMenuContributor;
import com.torturemod.api.torture.MenuButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Меню пленного: описание плена, состояние, пытки без предмета в руке, «отпустить» и «казнить». */
final class CaptiveMenu {
    static final int INFO_SLOT = 4;
    static final int FIRST_TYPE_SLOT = 10;
    static final int HEALTH_SLOT = 15;
    static final int RESISTANCE_SLOT = 16;
    static final int RELEASE_SLOT = 21;
    static final int EXECUTE_SLOT = 23;
    /** Слоты для кнопок других фич, например допроса. */
    static final List<Integer> EXTENSION_SLOTS = List.of(19, 25, 18, 20, 24, 26);

    private final FeatureContext context;
    private final CaptivityService captivity;
    private final ResistanceService resistance;
    private final ItemClassifier classifier;
    private final Torturer torturer;
    private final Function<String, TypeSettings> settings;
    private final List<CaptiveMenuContributor> contributors = new ArrayList<>();

    CaptiveMenu(FeatureContext context, CaptivityService captivity, ResistanceService resistance,
                ItemClassifier classifier, Torturer torturer, Function<String, TypeSettings> settings) {
        this.context = context;
        this.captivity = captivity;
        this.resistance = resistance;
        this.classifier = classifier;
        this.torturer = torturer;
        this.settings = settings;
    }

    void open(TargetId viewer, TargetId captiveId) {
        Optional<Captive> captive = captivity.find(captiveId);
        Optional<TargetState> target = context.platform().targets().state(captiveId);
        if (captive.isEmpty() || target.isEmpty()) {
            context.platform().menus().close(viewer);
            return;
        }
        ResistanceView view = resistance.view(captiveId)
                .orElse(new ResistanceView(captive.get().resistance(), 100, captive.get().broken(), java.util.Map.of()));
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(info(captive.get(), target.get()));
        int slot = FIRST_TYPE_SLOT;
        for (String type : TortureTypes.ALL) {
            entries.add(typeEntry(viewer, captiveId, type, slot++, view));
        }
        entries.add(new Menus.Entry(HEALTH_SLOT, "minecraft:golden_apple",
                text("torture.menu.health", round(target.get().health()), round(target.get().maxHealth())), List.of(), null));
        entries.add(new Menus.Entry(RESISTANCE_SLOT, view.broken() ? "minecraft:skeleton_skull" : "minecraft:iron_bars",
                view.broken() ? text("torture.menu.broken")
                        : text("torture.menu.resistance", round(view.value()), round(view.max())), List.of(), null));
        entries.add(new Menus.Entry(RELEASE_SLOT, "minecraft:lime_dye", text("torture.menu.release"), List.of(),
                () -> release(viewer, captiveId, target.get().name())));
        entries.add(view.broken()
                ? new Menus.Entry(EXECUTE_SLOT, "minecraft:red_dye", text("torture.menu.execute"), List.of(),
                        () -> execute(viewer, captiveId, target.get().name()))
                : new Menus.Entry(EXECUTE_SLOT, "minecraft:gray_dye", text("torture.menu.execute"),
                        List.of(text("torture.menu.execute_locked")), null));
        int extension = 0;
        for (CaptiveMenuContributor contributor : contributors) {
            Optional<MenuButton> button = contributor.button(viewer, captiveId);
            if (button.isPresent() && extension < EXTENSION_SLOTS.size()) {
                entries.add(new Menus.Entry(EXTENSION_SLOTS.get(extension++), button.get().itemId(), button.get().label(),
                        button.get().lore(), button.get().onClick()));
            }
        }
        context.platform().menus().open(viewer, new Menus.Menu(text("torture.menu.title", target.get().name()), 3, entries));
    }

    void addContributor(CaptiveMenuContributor contributor) {
        contributors.add(contributor);
    }

    private Menus.Entry info(Captive captive, TargetState target) {
        List<String> lore = new ArrayList<>();
        captive.captor().ifPresent(captor -> lore.add(text("torture.menu.captor", name(captor))));
        long minutes = Math.max(0, (context.clock().millis() - captive.capturedAtEpochMillis()) / 60000);
        lore.add(text("torture.menu.since", minutes));
        lore.add(captive.cell()
                .map(cell -> text("torture.menu.cell", cell.x() + " " + cell.y() + " " + cell.z()))
                .orElseGet(() -> text("torture.menu.no_cell")));
        return new Menus.Entry(INFO_SLOT, "minecraft:name_tag", target.name(), lore, null);
    }

    private Menus.Entry typeEntry(TargetId viewer, TargetId captive, String type, int slot, ResistanceView view) {
        List<String> lore = new ArrayList<>();
        double effectiveness = view.effectiveness().getOrDefault(type, 1.0);
        lore.add(text("torture.menu.effectiveness", Math.round(effectiveness * 100)));
        long cooldown = torturer.cooldownLeft(captive, type);
        lore.add(cooldown > 0 ? text("torture.cooldown", (cooldown + 999) / 1000) : text("torture.menu.ready"));
        Optional<ItemEntry> item = findItem(viewer, type);
        lore.add(item.map(found -> text("torture.menu.item", found.itemId())).orElseGet(() -> text("torture.menu.no_item")));
        Runnable onClick = item.isEmpty() ? null : () -> {
            Classification found = classifier.classify(item.get().itemId());
            torturer.torture(viewer, captive, java.util.Set.of(type), found.strength(),
                    () -> context.platform().items().spend(viewer, item.get().slot()));
            open(viewer, captive);
        };
        return new Menus.Entry(slot, settings.apply(type).icon(), torturer.typeName(type), lore, onClick);
    }

    private Optional<ItemEntry> findItem(TargetId viewer, String type) {
        return context.platform().inventories().contents(viewer, InventoryKind.MAIN).stream()
                .filter(entry -> classifier.classify(entry.itemId()).types().contains(type))
                .findFirst();
    }

    private void release(TargetId viewer, TargetId captive, String name) {
        context.platform().menus().close(viewer);
        if (!TortureFeature.allowed(context, captivity, viewer, captive, AccessRight.RELEASE)) {
            return;
        }
        if (captivity.release(captive, ReleaseReason.FREED)) {
            context.platform().feedback().message(viewer, text("torture.released", name));
        }
    }

    /** Казнь доступна только сломленному: сначала снимаем плен с причиной, затем убиваем. */
    private void execute(TargetId viewer, TargetId captive, String name) {
        context.platform().menus().close(viewer);
        if (!TortureFeature.allowed(context, captivity, viewer, captive, AccessRight.EXECUTE)) {
            return;
        }
        if (!resistance.isBroken(captive) || !captivity.release(captive, ReleaseReason.EXECUTED)) {
            return;
        }
        context.platform().combat().kill(captive);
        context.platform().feedback().message(viewer, text("torture.executed", name));
    }

    private String name(TargetId id) {
        return context.platform().targets().state(id).map(TargetState::name)
                .or(() -> context.platform().targets().playerName(id))
                .orElse(id.value().toString());
    }

    private String text(String key, Object... args) {
        return context.messages().text(key, args);
    }

    private static long round(double value) {
        return Math.round(value);
    }
}
