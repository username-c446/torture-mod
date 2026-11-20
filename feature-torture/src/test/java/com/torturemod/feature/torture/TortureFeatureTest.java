package com.torturemod.feature.torture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.ItemTraits;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.torture.TortureApplied;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.items.ItemsFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

class TortureFeatureTest {
    private static final String SWORD = "minecraft:iron_sword";
    private static final String FLINT = "minecraft:flint_and_steel";

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private ResistanceService resistance;
    private TargetId prisoner;
    private TargetId captor;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        platform.itemTraits.put(SWORD, new ItemTraits(SWORD, "minecraft", Set.of(), Set.of("sword"), 4));
        platform.itemTraits.put(FLINT, new ItemTraits(FLINT, "minecraft", Set.of(), Set.of("igniter"), 1));
        platform.itemTraits.put("minecraft:stick", new ItemTraits("minecraft:stick", "minecraft", Set.of(), Set.of(), 1));
        prisoner = platform.player("Prisoner", new Location("w", 0, 0, 0));
        captor = platform.player("Captor", new Location("w", 2, 0, 0));
        context = new FakeContext(platform);
    }

    private void start() {
        new CaptivityFeature().start(context);
        new ItemsFeature().start(context);
        new ResistanceFeature().start(context);
        new TortureFeature().start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        resistance = context.services().find(ResistanceService.class).orElseThrow();
        captivity.captureWithoutCell(prisoner, Optional.of(captor));
    }

    private Cancellation interact(TargetId target, String item, boolean sneaking) {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(captor, target, Optional.ofNullable(item), sneaking, cancellation));
        return cancellation;
    }

    private double resistanceOf(TargetId id) {
        return resistance.view(id).orElseThrow().value();
    }

    @Test
    void itemTorturesCaptiveAndSpendsItem() {
        start();
        platform.give(captor, 0, SWORD);

        assertTrue(interact(prisoner, SWORD, false).isCancelled());

        assertEquals(100 - 0.25, resistanceOf(prisoner), 1e-9);
        assertEquals(19, platform.health(prisoner), 1e-6);
        assertEquals(1, platform.spent.size());
        assertEquals("strike", context.published(TortureApplied.class).get(0).type());
        assertNotNull(platform.bars.get(prisoner.value() + "|torturemod:resistance:" + prisoner.value()));
    }

    @Test
    void cooldownBlocksRepeatUntilItPasses() {
        start();
        platform.give(captor, 0, SWORD);
        interact(prisoner, SWORD, false);

        interact(prisoner, SWORD, false);
        assertEquals(1, context.published(TortureApplied.class).size());
        assertTrue(platform.actionBarsOf(captor).contains("torture.cooldown 3"));

        context.clock.advanceSeconds(3);
        interact(prisoner, SWORD, false);
        assertEquals(2, context.published(TortureApplied.class).size());
    }

    @Test
    void vanillaImmunityMeansNoResistanceDrop() {
        start();
        platform.fireImmune.add(prisoner);

        interact(prisoner, FLINT, false);

        assertEquals(100, resistanceOf(prisoner));
        assertTrue(platform.spent.isEmpty());
        assertTrue(platform.actionBarsOf(captor).contains("torture.immune Prisoner torture.type.fire"));
    }

    @Test
    void ignoresFreeTargetsAndPlainItems() {
        start();
        TargetId free = platform.player("Free", new Location("w", 5, 0, 0));

        assertFalse(interact(free, SWORD, false).isCancelled());
        assertFalse(interact(prisoner, "minecraft:stick", false).isCancelled());
        assertFalse(interact(prisoner, null, false).isCancelled());
        assertTrue(context.published(TortureApplied.class).isEmpty());
    }

    @Test
    void regionPolicyForbidsTorture() {
        start();
        platform.regionPolicy = (actor, location, action) -> false;

        interact(prisoner, SWORD, false);

        assertEquals(100, resistanceOf(prisoner));
        assertTrue(platform.actionBarsOf(captor).contains("torture.region"));
    }

    @Test
    void sneakingOpensMenuWithTortureFromInventory() {
        start();
        platform.give(captor, 7, FLINT);

        assertTrue(interact(prisoner, null, true).isCancelled());
        assertEquals("torture.menu.title Prisoner", platform.openMenus.get(captor).title());

        platform.click(captor, CaptiveMenu.FIRST_TYPE_SLOT + 1);

        assertEquals(100 - 0.4, resistanceOf(prisoner), 1e-9);
        assertEquals(captor.value() + "|7", platform.spent.get(0));
    }

    @Test
    void menuReleasesCaptive() {
        start();
        interact(prisoner, null, true);

        platform.click(captor, CaptiveMenu.RELEASE_SLOT);

        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.FREED, context.published(CaptiveReleased.class).get(0).reason());
        assertNull(platform.openMenus.get(captor));
    }

    @Test
    void executionNeedsBrokenCaptive() {
        context.config.put("torture.types.strike.resistance_drop", 200.0);
        start();
        interact(prisoner, null, true);
        assertNull(platform.openMenus.get(captor).entries().stream()
                .filter(entry -> entry.slot() == CaptiveMenu.EXECUTE_SLOT).findFirst().orElseThrow().onClick());

        interact(prisoner, SWORD, false);
        assertTrue(resistance.isBroken(prisoner));
        assertTrue(platform.messagesOf(prisoner).contains("torture.you_are_broken"));

        interact(prisoner, null, true);
        platform.click(captor, CaptiveMenu.EXECUTE_SLOT);

        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.EXECUTED, context.published(CaptiveReleased.class).get(0).reason());
        assertTrue(platform.combatLog.contains("kill"));
    }
}
