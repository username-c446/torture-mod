package com.torturemod.feature.prison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.AccessRight;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.prison.EscapeHelped;
import com.torturemod.api.prison.PrisonEscaped;
import com.torturemod.api.prison.PrisonRight;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class PrisonFeatureTest extends PrisonTestBase {
    @BeforeEach
    void setUp() {
        setUpPrison();
    }

    @Test
    void markingWithoutCommandsCreatesPrisonAndConsole() {
        assertEquals(1, prisons.prisons().size());
        assertEquals(owner, prison().owner());
        assertEquals(31, prison().area().sizeX());
        assertTrue(registry.byId(prison().id()).orElseThrow().consoles.contains(lectern));
        assertTrue(messaged(owner, "prison.created"));
    }

    @Test
    void rightClickOnBlockIsNotAlsoConfirmation() {
        // ПКМ по блоку игра присылает ещё и как ПКМ в воздух: подтверждение в том же тике не срабатывает.
        platform.fire(new GameEvent.BlockAttacked(owner, new BlockLocation(W, 40, 0, 40), Optional.of(STICK), false,
                new Cancellation()));
        use(owner, new BlockLocation(W, 50, 5, 50), STICK, true);
        confirm(owner);
        assertEquals(1, prisons.prisons().size());
    }

    @Test
    void overlappingPrisonIsRefusedAndInnerAreaBecomesZone() {
        mark(stranger, new BlockLocation(W, 25, 0, 25), new BlockLocation(W, 45, 5, 45));
        confirm(stranger);
        assertEquals(1, prisons.prisons().size());
        assertTrue(messaged(stranger, "prison.mark.overlap"));

        mark(owner, new BlockLocation(W, 2, 0, 2), new BlockLocation(W, 8, 5, 8));
        confirm(owner);
        assertEquals(1, registry.byId(prison().id()).orElseThrow().zones.size());
    }

    @Test
    void shiftLeftClickResetsMarking() {
        mark(owner, new BlockLocation(W, 40, 0, 40), new BlockLocation(W, 50, 5, 50));
        platform.fire(new GameEvent.ArmSwung(owner, Optional.of(STICK), true));
        confirm(owner);
        assertEquals(1, prisons.prisons().size());
        assertTrue(messaged(owner, "prison.mark.incomplete"));
    }

    @Test
    void captiveInsideBecomesInmateAndLeavingIsEscapeWithAlarm() {
        captureInside();
        assertEquals(Optional.of(prison().id()), prisons.prisonOf(prisoner));
        platform.move(prisoner, new Location(W, 20, 1, 20));
        platform.tick(40);
        assertTrue(captivity.isCaptive(prisoner));

        platform.move(owner, new Location(W, 33, 1, 33));
        platform.move(prisoner, new Location(W, 36, 1, 36));
        platform.tick(20);
        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.ESCAPE, context.published(CaptiveReleased.class).get(0).reason());
        assertTrue(messaged(owner, "prison.alarm"));
        assertTrue(context.published(PrisonEscaped.class).get(0).seen());
    }

    @Test
    void unseenEscapeRaisesNoAlarm() {
        captureInside();
        platform.hiddenPairs.add(owner.value() + "|" + prisoner.value());
        platform.move(prisoner, new Location(W, 36, 1, 36));
        platform.tick(20);
        assertFalse(captivity.isCaptive(prisoner));
        assertFalse(messaged(owner, "prison.alarm"));
        assertFalse(context.published(PrisonEscaped.class).get(0).seen());
    }

    @Test
    void cellInsidePrisonDoesNotHoldInmate() {
        platform.hollowBox(W, 10, 0, 10, 14, 4, 14, "minecraft:iron_bars");
        platform.setBlock(W, 12, 0, 12, "minecraft:lodestone");
        platform.move(prisoner, new Location(W, 12.5, 1, 12.5));
        platform.fire(new GameEvent.EntityInteracted(owner, prisoner, Optional.of("minecraft:lead"), false,
                new Cancellation()));
        assertTrue(captivity.find(prisoner).orElseThrow().cell().isPresent());
        platform.move(prisoner, new Location(W, 25, 1, 25));
        platform.tick(120);
        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void strangersHaveNoRightsOverInmatesOrCaptureInside() {
        captureInside();
        Location where = new Location(W, 10, 1, 10);
        assertFalse(captivity.allows(stranger, AccessRight.TORTURE, Optional.of(prisoner), where));
        assertTrue(captivity.allows(owner, AccessRight.TORTURE, Optional.of(prisoner), where));
        // Клетка внутри чужой тюрьмы: захват поводком не срабатывает.
        platform.hollowBox(W, 20, 0, 20, 24, 4, 24, "minecraft:iron_bars");
        platform.setBlock(W, 22, 0, 22, "minecraft:lodestone");
        TargetId other = platform.player("Other", new Location(W, 22.5, 1, 22.5));
        platform.fire(new GameEvent.EntityInteracted(stranger, other, Optional.of("minecraft:lead"), false,
                new Cancellation()));
        assertFalse(captivity.isCaptive(other));
        assertTrue(platform.actionBarsOf(stranger).contains("captivity.failure.NOT_ALLOWED"));
    }

    @Test
    void wardenAddedFromConsoleHasPresetRights() {
        TargetId warden = platform.player("Warden", new Location(W, 6, 1, 6));
        openConsole(owner);
        click(owner, 12);
        click(owner, 45);
        clickLabel(owner, "Warden");
        clickLabel(owner, "prison.role.warden");
        assertTrue(prisons.hasRight(prison().id(), warden, PrisonRight.TORTURE));
        assertFalse(prisons.hasRight(prison().id(), warden, PrisonRight.SENTENCE));
        assertTrue(prisons.isFriend(prison().id(), warden));
        assertFalse(prisons.isFriend(prison().id(), stranger));
    }

    @Test
    void consoleOpensOnlyForTeam() {
        openConsole(stranger);
        assertFalse(platform.openMenus.containsKey(stranger));
        openConsole(owner);
        assertTrue(platform.openMenus.containsKey(owner));
    }

    @Test
    void zoneFlagsDecideForUncuffedInmate() {
        mark(owner, new BlockLocation(W, 2, 0, 2), new BlockLocation(W, 8, 5, 8));
        confirm(owner);
        captureInside();
        cuffs.uncuff(prisoner);
        Cancellation inZone = new Cancellation();
        platform.fire(new GameEvent.BlockBroken(prisoner, new BlockLocation(W, 3, 1, 3), inZone));
        assertFalse(inZone.isCancelled());
        Cancellation outside = new Cancellation();
        platform.fire(new GameEvent.BlockBroken(prisoner, new BlockLocation(W, 20, 1, 20), outside));
        assertTrue(outside.isCancelled());
        platform.food.add("minecraft:bread");
        Cancellation eat = new Cancellation();
        platform.fire(new GameEvent.ItemUsed(prisoner, "minecraft:bread", false, eat));
        assertFalse(eat.isCancelled());
        cuffs.cuff(prisoner);
        Cancellation cuffed = new Cancellation();
        platform.fire(new GameEvent.BlockBroken(prisoner, new BlockLocation(W, 3, 1, 3), cuffed));
        assertTrue(cuffed.isCancelled());
    }

    @Test
    void sentenceRunsOutAndFrees() {
        captureInside();
        openConsole(owner);
        click(owner, 10);
        click(owner, 0);
        click(owner, 13);
        assertEquals(10 * 60_000L, registry.inmates.get(prisoner).sentenceLeftMillis);
        platform.tick(20 * 601);
        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.SENTENCE_SERVED, context.published(CaptiveReleased.class).get(0).reason());
    }

    @Test
    void shearsDoNotFreeInmateAndMarkHelper() {
        captureInside();
        cuffs.uncuff(prisoner);
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(stranger, prisoner, Optional.of("minecraft:shears"), false,
                cancellation));
        assertTrue(cancellation.isCancelled());
        assertEquals(stranger, context.published(EscapeHelped.class).get(0).helper());
    }

    @Test
    void prisonAndInmatesSurviveRestart() {
        captureInside();
        FakePlatform restartedPlatform = new FakePlatform();
        restartedPlatform.player("Prisoner", new Location(W, 10, 1, 10));
        FakeContext restarted = new FakeContext(restartedPlatform, context.storage);
        startFeatures(restarted);
        PrisonRegistry loaded = (PrisonRegistry) restarted.services()
                .find(com.torturemod.api.prison.PrisonService.class).orElseThrow();
        assertEquals(1, loaded.prisons.size());
        assertEquals(Optional.of(prison().id()), loaded.prisonOf(prisoner));
        assertTrue(loaded.prisons.values().iterator().next().consoles.contains(lectern));
    }
}
