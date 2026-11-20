package com.torturemod.feature.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import com.torturemod.testkit.InMemoryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class OfflineFeatureTest {
    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private ResistanceService resistance;
    private TargetId prisoner;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        prisoner = platform.player("Prisoner", new Location("w", 0, 0, 0));
        context = new FakeContext(platform);
    }

    private void start() {
        new CaptivityFeature().start(context);
        new ResistanceFeature().start(context);
        new OfflineFeature().start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        resistance = context.services().find(ResistanceService.class).orElseThrow();
    }

    /** Игрок выходит: событие приходит, пока он ещё в мире, затем он пропадает. */
    private TargetState leave() {
        TargetState state = platform.targets.get(prisoner);
        platform.fire(new GameEvent.PlayerLeft(prisoner));
        platform.targets.remove(prisoner);
        return state;
    }

    private void join(TargetState state) {
        platform.targets.put(prisoner, state);
        platform.fire(new GameEvent.PlayerJoined(prisoner));
    }

    private double value() {
        return resistance.view(prisoner).orElseThrow().value();
    }

    @Test
    void leavingCaptiveLeavesBodyAndLosesResistance() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());

        TargetState state = leave();

        assertTrue(platform.bodies.contains(prisoner));
        assertEquals(90, value(), 1e-9);

        join(state);
        assertEquals(List.of(prisoner), platform.restoredBodies);
        assertTrue(platform.messagesOf(prisoner).contains("offline.penalty 1 5"));
    }

    @Test
    void freePlayerLeavesWithoutBody() {
        start();

        leave();

        assertTrue(platform.bodies.isEmpty());
    }

    @Test
    void repeatedExitsBreakCaptive() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());

        TargetState state = null;
        for (int i = 0; i < 5; i++) {
            state = leave();
            join(state);
        }

        assertTrue(resistance.isBroken(prisoner));
        assertTrue(platform.messagesOf(prisoner).contains("offline.broken_by_exits 5"));
    }

    @Test
    void releaseWhileOfflineRemovesBody() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());
        leave();

        captivity.release(prisoner, ReleaseReason.ADMIN);

        assertEquals(List.of(prisoner), platform.removedBodies);
        assertFalse(platform.bodies.contains(prisoner));
    }

    @Test
    void bodiesComeBackAfterRestart() {
        InMemoryStorage storage = context.storage;
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());
        leave();
        platform.bodies.clear();

        context = new FakeContext(platform, storage);
        start();

        assertTrue(platform.bodies.contains(prisoner));
    }

    @Test
    void penaltiesCanBeSwitchedOff() {
        context.config.put("offline.penalties.body", false);
        context.config.put("offline.penalties.resistance_loss", false);
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());

        leave();

        assertTrue(platform.bodies.isEmpty());
        assertEquals(100, value(), 1e-9);
    }
}
