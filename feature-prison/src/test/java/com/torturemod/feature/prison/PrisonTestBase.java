package com.torturemod.feature.prison;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Menus;
import com.torturemod.api.prison.PrisonInfo;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.cuffs.CuffsFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Тюрьма 0..30 × 0..10 × 0..30 владельца Owner с пультом-кафедрой; Prisoner внутри. */
abstract class PrisonTestBase {
    static final String W = "w";
    static final String STICK = "minecraft:stick";

    FakePlatform platform;
    FakeContext context;
    CaptivityService captivity;
    CuffsService cuffs;
    PrisonService prisons;
    PrisonRegistry registry;
    TargetId owner;
    TargetId prisoner;
    TargetId stranger;
    final BlockLocation lectern = new BlockLocation(W, 15, 0, 15);

    void setUpPrison() {
        platform = new FakePlatform();
        owner = platform.player("Owner", new Location(W, 5, 1, 5));
        prisoner = platform.player("Prisoner", new Location(W, 10, 1, 10));
        stranger = platform.player("Stranger", new Location(W, 12, 1, 12));
        context = new FakeContext(platform);
        startFeatures(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        cuffs = context.services().find(CuffsService.class).orElseThrow();
        prisons = context.services().find(PrisonService.class).orElseThrow();
        registry = (PrisonRegistry) prisons;
        mark(owner, new BlockLocation(W, 0, 0, 0), new BlockLocation(W, 30, 10, 30));
        confirm(owner);
        platform.setBlock(W, 15, 0, 15, "minecraft:lectern");
        use(owner, lectern, STICK, true);
    }

    void startFeatures(FakeContext target) {
        new CaptivityFeature().start(target);
        new ResistanceFeature().start(target);
        new CuffsFeature().start(target);
        new PrisonFeature().start(target);
    }

    PrisonInfo prison() {
        return prisons.prisons().iterator().next();
    }

    void mark(TargetId player, BlockLocation first, BlockLocation second) {
        platform.fire(new GameEvent.BlockAttacked(player, first, Optional.of(STICK), false, new Cancellation()));
        use(player, second, STICK, false);
        platform.tick(1);
    }

    void confirm(TargetId player) {
        platform.fire(new GameEvent.ItemUsed(player, STICK, true, new Cancellation()));
    }

    Cancellation use(TargetId player, BlockLocation block, String item, boolean sneaking) {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.BlockInteracted(player, block, Optional.ofNullable(item), sneaking, cancellation));
        return cancellation;
    }

    BlockLocation chest(int x, int z) {
        platform.setBlock(W, x, 0, z, "minecraft:chest");
        BlockLocation block = new BlockLocation(W, x, 0, z);
        platform.chests.put(block, new ArrayList<>());
        return block;
    }

    /** Shift+ПКМ палкой по сундуку times раз: роли идут по кругу сдача, награды, выкуп, склад. */
    void designate(BlockLocation chest, int times) {
        for (int i = 0; i < times; i++) {
            use(owner, chest, STICK, true);
            platform.tick(1);
        }
    }

    void openConsole(TargetId player) {
        use(player, lectern, null, false);
    }

    void click(TargetId viewer, int slot) {
        platform.click(viewer, slot);
    }

    /** Нажимает на кнопку открытого меню по подписи. */
    void clickLabel(TargetId viewer, String label) {
        Menus.Menu menu = platform.openMenus.get(viewer);
        Menus.Entry entry = menu.entries().stream().filter(e -> e.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("No " + label + " in " + menu.entries().stream().map(Menus.Entry::label).toList()));
        entry.onClick().run();
    }

    void captureInside() {
        captivity.captureWithoutCell(prisoner, Optional.of(owner));
    }

    static List<ItemStackData> items(String id, int count) {
        return List.of(ItemStackData.of(id, count));
    }

    boolean messaged(TargetId player, String prefix) {
        return platform.messagesOf(player).stream().anyMatch(message -> message.startsWith(prefix));
    }
}
