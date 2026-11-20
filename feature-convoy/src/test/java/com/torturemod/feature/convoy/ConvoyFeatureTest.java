package com.torturemod.feature.convoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ConvoyFeatureTest {
    private static final String WORLD = "w";
    private static final String BARS = "minecraft:iron_bars";
    private static final String LEAD = "minecraft:lead";

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private TargetId prisoner;
    private TargetId captor;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        cage(0);
        cage(20);
        prisoner = platform.player("Prisoner", new Location(WORLD, 2.5, 1, 2.5));
        captor = platform.player("Captor", new Location(WORLD, 6, 1, 2));
        context = new FakeContext(platform);
        new CaptivityFeature().start(context);
        new ConvoyFeature().start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        interact(prisoner, LEAD);
        assertTrue(captivity.isCaptive(prisoner));
    }

    /** Клетка 3×3×3 с магнетитом в полу, начиная с x. */
    private void cage(int x) {
        platform.hollowBox(WORLD, x, 0, 0, x + 4, 4, 4, BARS);
        platform.setBlock(WORLD, x + 2, 0, 2, "minecraft:lodestone");
    }

    private Cancellation interact(TargetId target, String item) {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(captor, target, Optional.ofNullable(item), false, cancellation));
        return cancellation;
    }

    @Test
    void leashSuspendsCellAndUnleashInAnotherCellMovesThere() {
        assertTrue(interact(prisoner, LEAD).isCancelled());
        assertEquals(captor, platform.ropes.get(prisoner));

        platform.move(prisoner, new Location(WORLD, 22.5, 1, 2.5));
        platform.move(captor, new Location(WORLD, 26, 1, 2));
        platform.tick(40);
        assertTrue(captivity.isCaptive(prisoner));

        interact(prisoner, LEAD);
        assertTrue(platform.ropes.isEmpty());
        assertEquals(Optional.of(new BlockLocation(WORLD, 22, 1, 2)), captivity.find(prisoner).orElseThrow().cell());

        // Новая клетка держит как прежняя.
        platform.move(prisoner, new Location(WORLD, 40, 1, 40));
        platform.tick(20);
        assertFalse(captivity.isCaptive(prisoner));
    }

    @Test
    void leashPullsCaptiveTowardsHolder() {
        interact(prisoner, LEAD);
        platform.move(prisoner, new Location(WORLD, 0, 1, 0));
        platform.move(captor, new Location(WORLD, 7, 1, 0));

        platform.tick(2);

        assertEquals(1, platform.pushes.size());
        assertTrue(platform.pushes.get(0).endsWith("|1.0|0.0"));
    }

    @Test
    void unleashOutsideCellStartsReturnTimer() {
        context.config.put("convoy.return_seconds", 30);
        interact(prisoner, LEAD);
        platform.move(prisoner, new Location(WORLD, 50, 1, 50));
        platform.move(captor, new Location(WORLD, 52, 1, 50));
        interact(prisoner, LEAD);
        assertTrue(platform.messagesOf(captor).contains("convoy.return_timer Prisoner 30"));

        context.clock.advanceSeconds(20);
        platform.tick(20);
        assertTrue(captivity.isCaptive(prisoner));

        context.clock.advanceSeconds(11);
        platform.tick(20);
        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.LEFT_CELL, context.published(CaptiveReleased.class).get(0).reason());
    }

    @Test
    void returningIntoACellBeforeTheTimerKeepsCaptive() {
        interact(prisoner, LEAD);
        platform.move(prisoner, new Location(WORLD, 50, 1, 50));
        platform.move(captor, new Location(WORLD, 52, 1, 50));
        interact(prisoner, LEAD);

        platform.move(prisoner, new Location(WORLD, 22.5, 1, 2.5));
        context.clock.advanceSeconds(10);
        platform.tick(20);
        context.clock.advanceSeconds(120);
        platform.tick(40);

        assertTrue(captivity.isCaptive(prisoner));
        assertEquals(Optional.of(new BlockLocation(WORLD, 22, 1, 2)), captivity.find(prisoner).orElseThrow().cell());
    }

    @Test
    void stretchedLeashSnaps() {
        interact(prisoner, LEAD);
        platform.move(captor, new Location(WORLD, 60, 1, 2));

        platform.tick(2);

        assertTrue(platform.ropes.isEmpty());
        assertTrue(platform.actionBarsOf(captor).contains("convoy.snapped Prisoner"));
    }

    @Test
    void ledCaptiveRidesAndCannotGetOff() {
        TargetId boat = platform.mob("minecraft:boat", new Location(WORLD, 7, 1, 2));
        platform.vehicles.add(boat);
        interact(prisoner, LEAD);

        assertTrue(interact(boat, null).isCancelled());
        assertEquals(boat, platform.riding.get(prisoner));

        Cancellation getOff = new Cancellation();
        platform.fire(new GameEvent.DismountAttempted(prisoner, boat, getOff));
        assertTrue(getOff.isCancelled());

        // Палач снимает пленного с транспорта и отпускает поводок.
        interact(prisoner, LEAD);
        assertFalse(platform.riding.containsKey(prisoner));
    }

    @Test
    void anotherPlayerCannotTakeTheLeash() {
        interact(prisoner, LEAD);
        TargetId other = platform.player("Other", new Location(WORLD, 6, 1, 3));

        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(other, prisoner, Optional.of(LEAD), false, cancellation));

        assertEquals(captor, platform.ropes.get(prisoner));
        assertTrue(platform.actionBarsOf(other).contains("convoy.already_led Prisoner Captor"));
    }
}
