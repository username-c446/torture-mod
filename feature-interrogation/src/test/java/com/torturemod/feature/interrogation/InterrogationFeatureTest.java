package com.torturemod.feature.interrogation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Menus;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.items.ItemsFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.feature.torture.TortureFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class InterrogationFeatureTest {
    private static final int BUTTON_SLOT = 19;

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private ResistanceService resistance;
    private TargetId prisoner;
    private TargetId captor;
    private TargetId friend;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        prisoner = platform.player("Prisoner", new Location("w", 0, 0, 0));
        friend = platform.player("Friend", new Location("w", 3, 0, 0));
        captor = platform.player("Captor", new Location("w", 200, 0, 0));
        platform.teams.put(prisoner, "Bandits");
        platform.teams.put(friend, "Bandits");
        platform.respawnPoints.put(prisoner, new BlockLocation("minecraft:overworld", 10, 64, -20));
        platform.give(prisoner, 0, "minecraft:diamond");
        platform.enderChests.put(prisoner, Map.of(0, "minecraft:emerald"));
        context = new FakeContext(platform);
        context.config.put("interrogation.allies.sample_seconds", 1);
        context.config.put("interrogation.allies.min_samples", 3);
        new CaptivityFeature().start(context);
        new ItemsFeature().start(context);
        new ResistanceFeature().start(context);
        new TortureFeature().start(context);
        new InterrogationFeature().start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        resistance = context.services().find(ResistanceService.class).orElseThrow();
    }

    private List<String> interrogate() {
        platform.messages.remove(captor);
        platform.fire(new GameEvent.EntityInteracted(captor, prisoner, Optional.empty(), true, new Cancellation()));
        platform.click(captor, BUTTON_SLOT);
        return platform.messagesOf(captor);
    }

    @Test
    void revealsMoreAsResistanceDrops() {
        platform.tick(100);
        captivity.captureWithoutCell(prisoner, Optional.of(captor));

        List<String> silent = interrogate();
        assertTrue(silent.contains("interrogation.locked interrogation.source.allies 50"));
        assertTrue(silent.contains("interrogation.locked interrogation.source.inventory 0"));

        resistance.weaken(prisoner, "strike", 60);
        List<String> allies = interrogate();
        assertTrue(allies.contains("interrogation.source.allies: interrogation.allies.team Bandits Friend"), allies.toString());
        assertTrue(allies.contains("interrogation.source.allies: interrogation.allies.near Friend"), allies.toString());
        assertTrue(allies.contains("interrogation.locked interrogation.source.coordinates 25"));

        resistance.breakNow(prisoner);
        List<String> all = interrogate();
        assertTrue(all.contains("interrogation.source.coordinates: interrogation.coordinates.respawn minecraft:overworld 10 64 -20"));
        assertTrue(all.contains("interrogation.source.inventory: minecraft:diamond ×1"));
        assertTrue(all.contains("interrogation.source.ender_chest: minecraft:emerald ×1"));
    }

    @Test
    void timeInCaptivityDoesNotMakeCaptorsAllies() {
        captivity.captureWithoutCell(prisoner, Optional.of(captor));
        platform.move(captor, new Location("w", 1, 0, 0));
        platform.tick(200);
        resistance.breakNow(prisoner);

        List<String> lines = interrogate();
        assertTrue(lines.stream().noneMatch(line -> line.contains("near")), lines.toString());
    }

    @Test
    void mobsHaveNoInterrogationButton() {
        TargetId cow = platform.mob("minecraft:cow", new Location("w", 0, 0, 0));
        captivity.captureWithoutCell(cow, Optional.of(captor));

        platform.fire(new GameEvent.EntityInteracted(captor, cow, Optional.empty(), true, new Cancellation()));

        Menus.Menu menu = platform.openMenus.get(captor);
        assertEquals(0, menu.entries().stream().filter(entry -> entry.slot() == BUTTON_SLOT).count());
    }
}
