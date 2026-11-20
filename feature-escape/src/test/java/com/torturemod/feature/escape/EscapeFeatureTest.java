package com.torturemod.feature.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class EscapeFeatureTest {
    private static final String SHEARS = "minecraft:shears";

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private ResistanceService resistance;
    private TargetId prisoner;
    private TargetId helper;
    /** Следующее «случайное» число: 0 — прыжок и успех, 0.99 — присед и неудача. */
    private double next;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        prisoner = platform.player("Prisoner", new Location("w", 0, 0, 0));
        helper = platform.player("Helper", new Location("w", 2, 0, 0));
        context = new FakeContext(platform);
        context.config.put("escape.max_chance_percent", 100.0);
        new CaptivityFeature().start(context);
        new ResistanceFeature().start(context);
        new EscapeFeature(() -> next).start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        resistance = context.services().find(ResistanceService.class).orElseThrow();
        captivity.captureWithoutCell(prisoner, Optional.empty());
    }

    private void passSeconds(long seconds) {
        context.clock.advanceSeconds(seconds);
        platform.tick(5);
    }

    private void press(GameEvent.Input input) {
        platform.fire(new GameEvent.InputPressed(prisoner, input));
    }

    private Cancellation cut() {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(helper, prisoner, Optional.of(SHEARS), false, cancellation));
        return cancellation;
    }

    private boolean sequenceOffered() {
        return platform.messagesOf(prisoner).stream().anyMatch(message -> message.startsWith("escape.sequence"));
    }

    @Test
    void correctSequenceGivesAChanceToEscape() {
        next = 0;
        passSeconds(179);
        assertFalse(sequenceOffered());

        passSeconds(1);
        assertTrue(platform.messagesOf(prisoner).contains(
                "escape.sequence escape.input.JUMP → escape.input.JUMP → escape.input.JUMP"));
        press(GameEvent.Input.JUMP);
        press(GameEvent.Input.JUMP);
        press(GameEvent.Input.JUMP);

        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.ESCAPE, context.published(CaptiveReleased.class).get(0).reason());
    }

    @Test
    void wrongInputFailsAndDelaysTheNextAttempt() {
        next = 0;
        passSeconds(180);
        press(GameEvent.Input.SNEAK);

        assertTrue(captivity.isCaptive(prisoner));
        assertTrue(platform.messagesOf(prisoner).contains("escape.missed 360"));
        platform.messages.clear();
        passSeconds(180);
        assertFalse(sequenceOffered());
        passSeconds(180);
        assertTrue(sequenceOffered());
    }

    @Test
    void slowPlayerTimesOut() {
        next = 0;
        passSeconds(180);
        passSeconds(9);

        assertTrue(platform.messagesOf(prisoner).contains("escape.timeout 360"));
        press(GameEvent.Input.JUMP);
        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void lostRollKeepsCaptive() {
        context.config.put("escape.max_chance_percent", 50.0);
        next = 0;
        passSeconds(180);
        press(GameEvent.Input.JUMP);
        press(GameEvent.Input.JUMP);
        next = 0.99;
        press(GameEvent.Input.JUMP);

        assertTrue(captivity.isCaptive(prisoner));
        assertTrue(platform.messagesOf(prisoner).contains("escape.failed 360"));
    }

    @Test
    void lowerResistanceMeansLowerChance() {
        context.config.put("escape.max_chance_percent", 25.0);
        resistance.weaken(prisoner, "strike", 80);
        next = 0;
        passSeconds(180);
        press(GameEvent.Input.JUMP);
        press(GameEvent.Input.JUMP);
        // Шанс 25 % × 0,2 = 5 %: число 0,06 его уже не проходит.
        next = 0.06;
        press(GameEvent.Input.JUMP);

        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void brokenCaptiveDoesNotTry() {
        resistance.breakNow(prisoner);
        next = 0;

        passSeconds(400);

        assertFalse(sequenceOffered());
        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void mobsRollWithoutSequence() {
        context.config.put("escape.max_chance_percent", 50.0);
        TargetId cow = platform.mob("minecraft:cow", new Location("w", 5, 0, 0));
        captivity.captureWithoutCell(cow, Optional.empty());
        next = 0.99;
        passSeconds(180);
        assertTrue(captivity.isCaptive(cow));

        next = 0;
        passSeconds(360);
        assertFalse(captivity.isCaptive(cow));
    }

    @Test
    void helperCutsTheCaptiveFreeWithShears() {
        platform.give(helper, 0, SHEARS);

        assertTrue(cut().isCancelled());
        for (int i = 0; i < 5; i++) {
            context.clock.advanceSeconds(1);
            platform.tick(5);
            cut();
        }

        assertFalse(captivity.isCaptive(prisoner));
        assertTrue(platform.messagesOf(prisoner).contains("escape.rescue.captive Helper"));
        assertEquals(1, platform.spent.size());
    }

    @Test
    void rescueStopsWhenHelperPausesWalksAwayOrIsHurt() {
        cut();
        context.clock.advanceSeconds(2);
        platform.tick(5);
        assertTrue(platform.actionBarsOf(helper).contains("escape.rescue.interrupted"));

        platform.actionBars.clear();
        cut();
        platform.move(helper, new Location("w", 20, 0, 0));
        platform.tick(5);
        assertTrue(platform.actionBarsOf(helper).contains("escape.rescue.interrupted"));

        platform.actionBars.clear();
        platform.move(helper, new Location("w", 2, 0, 0));
        cut();
        platform.fire(new GameEvent.Damaged(helper, Optional.empty(), 1, new Cancellation()));
        assertTrue(platform.actionBarsOf(helper).contains("escape.rescue.interrupted"));
        assertTrue(captivity.isCaptive(prisoner));
    }
}
