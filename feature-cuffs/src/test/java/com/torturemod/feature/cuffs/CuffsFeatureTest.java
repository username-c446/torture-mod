package com.torturemod.feature.cuffs;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class CuffsFeatureTest {
    private static final String CHAIN = CuffsFeature.HANDCUFFS;
    private static final String SHEARS = "minecraft:shears";

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private CuffsService cuffs;
    private TargetId prisoner;
    private TargetId captor;
    private TargetId helper;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        prisoner = platform.player("Prisoner", new Location("w", 0, 0, 0));
        captor = platform.player("Captor", new Location("w", 2, 0, 0));
        helper = platform.player("Helper", new Location("w", 0, 0, 2));
        context = new FakeContext(platform);
        new CaptivityFeature().start(context);
        new ResistanceFeature().start(context);
        new CuffsFeature().start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        cuffs = context.services().find(CuffsService.class).orElseThrow();
        captivity.captureWithoutCell(prisoner, Optional.of(captor));
    }

    private Cancellation click(TargetId actor, String item) {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(actor, prisoner, Optional.ofNullable(item), false, cancellation));
        return cancellation;
    }

    /** Нажатия раз в секунду, пока не пройдёт нужное время. */
    private void clickFor(TargetId actor, String item, int seconds) {
        click(actor, item);
        for (int i = 0; i < seconds; i++) {
            context.clock.advanceSeconds(1);
            click(actor, item);
        }
    }

    private Cancellation breakBlock() {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.BlockBroken(prisoner, new BlockLocation("w", 1, 0, 0), cancellation));
        return cancellation;
    }

    @Test
    void cuffedOnCaptureAndCuffsBlockHands() {
        assertTrue(cuffs.isCuffed(prisoner));
        assertTrue(breakBlock().isCancelled());
    }

    @Test
    void withoutCuffsOutsidePrisonHandsAreFree() {
        cuffs.uncuff(prisoner);
        assertFalse(breakBlock().isCancelled());
    }

    @Test
    void noCuffsOnCaptureWhenDisabled() {
        TargetId other = platform.player("Other", new Location("w", 5, 0, 0));
        context.config.put("cuffs.on_capture", false);
        captivity.captureWithoutCell(other, Optional.of(captor));
        assertFalse(cuffs.isCuffed(other));
    }

    @Test
    void cuffingTakesSeveralClicks() {
        cuffs.uncuff(prisoner);
        platform.give(captor, 0, CHAIN);
        assertTrue(click(captor, CHAIN).isCancelled());
        assertFalse(cuffs.isCuffed(prisoner));
        clickFor(captor, CHAIN, 3);
        assertTrue(cuffs.isCuffed(prisoner));
        assertTrue(platform.consumed.contains(captor.value() + "|" + CHAIN + "|1"));
    }

    @Test
    void captorUncuffsWithEmptyHandAndGetsChainBack() {
        clickFor(captor, null, 3);
        assertFalse(cuffs.isCuffed(prisoner));
        assertEquals(CHAIN, platform.givenTo(captor).get(0).itemId());
        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void strangerCannotUncuffWithHands() {
        clickFor(helper, null, 3);
        assertTrue(cuffs.isCuffed(prisoner));
        assertTrue(platform.actionBarsOf(helper).contains("cuffs.no_right"));
    }

    @Test
    void helperCutsCuffsWithShearsButCaptivityStays() {
        platform.give(helper, 0, SHEARS);
        clickFor(helper, SHEARS, 5);
        assertFalse(cuffs.isCuffed(prisoner));
        assertTrue(captivity.isCaptive(prisoner));
        assertTrue(platform.spent.contains(helper.value() + "|0"));
    }

    @Test
    void movingAwayInterrupts() {
        cuffs.uncuff(prisoner);
        click(captor, CHAIN);
        platform.move(captor, new Location("w", 20, 0, 0));
        platform.tick(5);
        assertTrue(platform.actionBarsOf(captor).contains("cuffs.interrupted"));
        platform.move(captor, new Location("w", 2, 0, 0));
        context.clock.advanceSeconds(3);
        click(captor, CHAIN);
        assertFalse(cuffs.isCuffed(prisoner));
    }

    @Test
    void handcuffsAreCraftedFromChainsAndIron() {
        assertEquals("minecraft:chain", platform.customItems.get(CHAIN).baseItemId());
        assertEquals(List.of("CIC"), platform.recipes.get(0).pattern());
        assertEquals(CHAIN, platform.recipes.get(0).resultId());
    }

    @Test
    void plainChainNoLongerCuffs() {
        cuffs.uncuff(prisoner);
        platform.give(captor, 0, "minecraft:chain");
        assertFalse(click(captor, "minecraft:chain").isCancelled());
    }

    @Test
    void cuffsSurviveRestart() {
        FakeContext restarted = new FakeContext(new FakePlatform(), context.storage);
        restarted.platform.player("Prisoner", new Location("w", 0, 0, 0));
        new CaptivityFeature().start(restarted);
        new ResistanceFeature().start(restarted);
        new CuffsFeature().start(restarted);
        assertTrue(restarted.services().find(CuffsService.class).orElseThrow().isCuffed(prisoner));
    }
}
