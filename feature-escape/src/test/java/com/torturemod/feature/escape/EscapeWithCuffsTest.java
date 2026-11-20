package com.torturemod.feature.escape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.cuffs.CuffsFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/** С наручниками удачная попытка снимает их, а не плен; без наручников попыток нет. */
class EscapeWithCuffsTest {
    @Test
    void successfulAttemptSlipsCuffsOnly() {
        FakePlatform platform = new FakePlatform();
        TargetId prisoner = platform.player("Prisoner", new Location("w", 0, 0, 0));
        FakeContext context = new FakeContext(platform);
        context.config.put("escape.max_chance_percent", 100.0);
        new CaptivityFeature().start(context);
        new ResistanceFeature().start(context);
        new CuffsFeature().start(context);
        new EscapeFeature(() -> 0).start(context);
        CaptivityService captivity = context.services().find(CaptivityService.class).orElseThrow();
        CuffsService cuffs = context.services().find(CuffsService.class).orElseThrow();
        captivity.captureWithoutCell(prisoner, Optional.empty());
        assertTrue(cuffs.isCuffed(prisoner));

        context.clock.advanceSeconds(181);
        platform.tick(5);
        for (int i = 0; i < 3; i++) {
            platform.fire(new GameEvent.InputPressed(prisoner, GameEvent.Input.JUMP));
        }
        assertFalse(cuffs.isCuffed(prisoner));
        assertTrue(captivity.isCaptive(prisoner));
        assertTrue(platform.messagesOf(prisoner).contains("escape.slipped"));

        int before = platform.messagesOf(prisoner).size();
        context.clock.advanceSeconds(400);
        platform.tick(5);
        assertTrue(platform.messagesOf(prisoner).stream().skip(before).noneMatch(m -> m.startsWith("escape.sequence")));
    }
}
