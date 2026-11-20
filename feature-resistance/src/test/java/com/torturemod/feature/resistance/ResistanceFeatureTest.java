package com.torturemod.feature.resistance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.resistance.CaptiveBroken;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ResistanceFeatureTest {
    private static final Location PLACE = new Location("w", 0, 0, 0);

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private ResistanceService resistance;
    private TargetId prisoner;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        prisoner = platform.player("Prisoner", PLACE);
        context = new FakeContext(platform);
    }

    private void start() {
        new CaptivityFeature().start(context);
        new ResistanceFeature().start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        resistance = context.services().find(ResistanceService.class).orElseThrow();
    }

    private double value(TargetId id) {
        return resistance.view(id).orElseThrow().value();
    }

    @Test
    void habituationWeakensRepeatedTypeAndRecoversOverTime() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());

        assertEquals(10, resistance.weaken(prisoner, "strike", 10), 1e-9);
        assertEquals(8.5, resistance.weaken(prisoner, "strike", 10), 1e-9);
        // Другой тип не затронут привыканием.
        assertEquals(10, resistance.weaken(prisoner, "fire", 10), 1e-9);
        assertEquals(71.5, value(prisoner), 1e-9);

        context.clock.advanceSeconds(100);
        assertEquals(1.0, resistance.view(prisoner).orElseThrow().effectiveness().get("strike"), 1e-9);
    }

    @Test
    void breaksAtZeroAndStaysBroken() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());

        resistance.weaken(prisoner, "strike", 150);

        assertTrue(resistance.isBroken(prisoner));
        assertEquals(1, context.published(CaptiveBroken.class).size());
        assertTrue(captivity.find(prisoner).orElseThrow().broken());
        assertEquals(0, resistance.weaken(prisoner, "fire", 10));
    }

    @Test
    void recoversOnlyAfterDelayAndOnlyOnline() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());
        resistance.weaken(prisoner, "strike", 50);

        platform.tick(20);
        assertEquals(50, value(prisoner), 1e-9);

        context.clock.advanceSeconds(61);
        platform.tick(20);
        assertEquals(50 + 100.0 / 3600, value(prisoner), 1e-9);

        platform.targets.remove(prisoner);
        platform.tick(200);
        assertEquals(50 + 100.0 / 3600, value(prisoner), 1e-9);
    }

    @Test
    void mobProfilesSetMaxAndVulnerability() {
        context.config.put("resistance.mobs.minecraft:villager.max", 60);
        context.config.put("resistance.mobs.minecraft:villager.vulnerability.fire", 2.0);
        start();
        TargetId villager = platform.mob("minecraft:villager", PLACE);
        captivity.captureWithoutCell(villager, Optional.empty());

        assertEquals(60, resistance.view(villager).orElseThrow().max());
        assertEquals(20, resistance.weaken(villager, "fire", 10), 1e-9);
    }

    @Test
    void savedResistanceSurvivesRestart() {
        start();
        captivity.captureWithoutCell(prisoner, Optional.empty());
        resistance.weaken(prisoner, "strike", 30);
        platform.tick(100);
        assertEquals(70, context.storage.records.get(prisoner).resistance(), 1e-9);

        FakeContext restarted = new FakeContext(platform, context.storage);
        context = restarted;
        start();
        assertEquals(70, value(prisoner), 1e-9);
        assertFalse(resistance.isBroken(prisoner));
    }
}
