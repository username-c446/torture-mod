package com.torturemod.feature.captivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptiveCaptured;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.CaptureMethod;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Permissions;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import com.torturemod.testkit.InMemoryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class CaptivityFeatureTest {
    private static final String WORLD = "minecraft:overworld";
    private static final String BARS = "minecraft:iron_bars";
    private static final String LEAD = "minecraft:lead";
    private static final Location INSIDE = new Location(WORLD, 2.5, 1, 2.5);

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private TargetId prisoner;
    private TargetId captor;
    private CaptivityFeature feature;
    private final java.util.Map<String, Object> config = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        // Клетка 3×3×3 внутри, магнетит в полу.
        platform.hollowBox(WORLD, 0, 0, 0, 4, 4, 4, BARS);
        platform.setBlock(WORLD, 2, 0, 2, "minecraft:lodestone");
        prisoner = platform.player("Prisoner", INSIDE);
        captor = platform.player("Captor", new Location(WORLD, 6, 1, 2));
        context = start(new InMemoryStorage());
    }

    private FakeContext start(InMemoryStorage storage) {
        // Перезапуск как на сервере: прежний экземпляр останавливается.
        if (feature != null) {
            feature.stop();
        }
        FakeContext started = new FakeContext(platform, storage);
        started.config.putAll(config);
        feature = new CaptivityFeature();
        feature.start(started);
        captivity = started.services().find(CaptivityService.class).orElseThrow();
        return started;
    }

    private Cancellation interact(TargetId actor, TargetId target, String item) {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(actor, target, Optional.ofNullable(item), false, cancellation));
        return cancellation;
    }

    @Test
    void capturesTargetInClosedCellWithMarker() {
        Cancellation cancellation = interact(captor, prisoner, LEAD);

        assertTrue(cancellation.isCancelled());
        assertTrue(captivity.isCaptive(prisoner));
        assertEquals(List.of(captor.value() + "|" + LEAD + "|1"), platform.consumed);
        assertTrue(context.storage.records.containsKey(prisoner));
        assertEquals(CaptureMethod.CAGE, context.published(CaptiveCaptured.class).get(0).method());
        assertEquals(Optional.of(BlockLocation.of(INSIDE)), captivity.find(prisoner).orElseThrow().cell());
        assertTrue(platform.messagesOf(prisoner).contains("captivity.you_are_captured"));
    }

    @Test
    void ignoresCellWithoutMarker() {
        platform.setBlock(WORLD, 2, 0, 2, BARS);

        assertFalse(interact(captor, prisoner, LEAD).isCancelled());
        assertFalse(captivity.isCaptive(prisoner));
    }

    @Test
    void ignoresOpenCell() {
        platform.setBlock(WORLD, 4, 2, 2, FakePlatform.AIR);

        assertFalse(interact(captor, prisoner, LEAD).isCancelled());
        assertFalse(captivity.isCaptive(prisoner));
    }

    @Test
    void ignoresCellLargerThanLimit() {
        config.put("captivity.cell.max_volume", 10);
        context = start(new InMemoryStorage());

        assertFalse(interact(captor, prisoner, LEAD).isCancelled());
    }

    @Test
    void ignoresOtherItems() {
        assertFalse(interact(captor, prisoner, "minecraft:stick").isCancelled());
        assertFalse(interact(captor, prisoner, null).isCancelled());
        assertFalse(captivity.isCaptive(prisoner));
    }

    @Test
    void markerCapturesEveryoneInside() {
        TargetId zombie = platform.mob("minecraft:zombie", new Location(WORLD, 1.5, 1, 1.5));
        Cancellation cancellation = new Cancellation();

        platform.fire(new GameEvent.BlockInteracted(captor, new BlockLocation(WORLD, 2, 0, 2), Optional.of(LEAD), false,
                cancellation));

        assertTrue(cancellation.isCancelled());
        assertTrue(captivity.isCaptive(prisoner));
        assertTrue(captivity.isCaptive(zombie));
        assertTrue(platform.persistent.contains(zombie));
        assertFalse(captivity.isCaptive(captor));
    }

    @Test
    void blocksActionsOfCaptivePlayer() {
        interact(captor, prisoner, LEAD);

        Cancellation breaking = new Cancellation();
        platform.fire(new GameEvent.BlockBroken(prisoner, new BlockLocation(WORLD, 0, 1, 1), breaking));
        Cancellation using = new Cancellation();
        platform.fire(new GameEvent.ItemUsed(prisoner, "minecraft:ender_pearl", false, using));
        Cancellation attacking = new Cancellation();
        platform.fire(new GameEvent.AttackAttempted(prisoner, captor, attacking));
        Cancellation captorBreaking = new Cancellation();
        platform.fire(new GameEvent.BlockBroken(captor, new BlockLocation(WORLD, 9, 9, 9), captorBreaking));

        assertTrue(breaking.isCancelled());
        assertTrue(using.isCancelled());
        assertTrue(attacking.isCancelled());
        assertFalse(captorBreaking.isCancelled());
        assertTrue(platform.actionBarsOf(prisoner).contains("captivity.action_blocked"));
    }

    @Test
    void captiveChatIsHeardOnlyNearby() {
        TargetId far = platform.player("Far", new Location(WORLD, 100, 1, 100));
        interact(captor, prisoner, LEAD);
        Cancellation cancellation = new Cancellation();

        platform.fire(new GameEvent.ChatSent(prisoner, "help", cancellation));

        assertTrue(cancellation.isCancelled());
        assertTrue(platform.messagesOf(captor).contains("captivity.local_chat Prisoner help"));
        assertTrue(platform.messagesOf(far).isEmpty());
    }

    @Test
    void captiveCommandsAreLimitedToWhitelist() {
        config.put("captivity.restrictions.allowed_commands", List.of("tell"));
        context = start(new InMemoryStorage());
        interact(captor, prisoner, LEAD);
        Cancellation blocked = new Cancellation();
        Cancellation allowed = new Cancellation();

        platform.fire(new GameEvent.CommandIssued(prisoner, "spawn", blocked));
        platform.fire(new GameEvent.CommandIssued(prisoner, "minecraft:tell Captor hi", allowed));

        assertTrue(blocked.isCancelled());
        assertFalse(allowed.isCancelled());
    }

    @Test
    void leavingCellReleases() {
        interact(captor, prisoner, LEAD);
        platform.move(prisoner, new Location(WORLD, 10, 1, 10));

        platform.tick(20);

        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.LEFT_CELL, context.published(CaptiveReleased.class).get(0).reason());
        assertFalse(context.storage.records.containsKey(prisoner));
    }

    @Test
    void brokenWallReleasesOnNextTick() {
        interact(captor, prisoner, LEAD);
        BlockLocation wall = new BlockLocation(WORLD, 4, 2, 2);

        platform.fire(new GameEvent.BlockBroken(captor, wall, new Cancellation()));
        platform.blocks.put(wall, FakePlatform.AIR);
        platform.tick(1);

        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.CELL_BROKEN, context.published(CaptiveReleased.class).get(0).reason());
    }

    @Test
    void unloadedCellIsNotReleased() {
        interact(captor, prisoner, LEAD);
        platform.unloadedWorlds.add(WORLD);

        platform.tick(100);

        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void deathReleases() {
        interact(captor, prisoner, LEAD);

        platform.fire(new GameEvent.Died(prisoner));

        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.DEATH, context.published(CaptiveReleased.class).get(0).reason());
    }

    @Test
    void releasedTargetIsImmuneForAWhile() {
        interact(captor, prisoner, LEAD);
        captivity.release(prisoner, ReleaseReason.ADMIN);

        interact(captor, prisoner, LEAD);
        assertFalse(captivity.isCaptive(prisoner));
        assertTrue(platform.actionBarsOf(captor).contains("captivity.failure.RECENTLY_RELEASED"));

        context.clock.advanceSeconds(301);
        interact(captor, prisoner, LEAD);
        assertTrue(captivity.isCaptive(prisoner));
    }

    @Test
    void respectsImmunityBlacklistAndRegions() {
        platform.grant(prisoner, Permissions.IMMUNE);
        interact(captor, prisoner, LEAD);
        assertFalse(captivity.isCaptive(prisoner));

        TargetId wither = platform.mob("minecraft:wither", new Location(WORLD, 1.5, 1, 3.5));
        interact(captor, wither, LEAD);
        assertFalse(captivity.isCaptive(wither));

        TargetId cow = platform.mob("minecraft:cow", new Location(WORLD, 3.5, 1, 1.5));
        platform.regionPolicy = (actor, location, action) -> false;
        interact(captor, cow, LEAD);
        assertFalse(captivity.isCaptive(cow));
        assertTrue(platform.actionBarsOf(captor).contains("captivity.failure.REGION"));
    }

    @Test
    void adminCommandsCaptureListAndRelease() {
        TargetId outside = platform.player("Outside", new Location(WORLD, 50, 1, 50));

        assertEquals(List.of("command.captured Outside"), context.run(Optional.empty(), "capture Outside"));
        assertTrue(captivity.isCaptive(outside));
        assertEquals(Optional.empty(), captivity.find(outside).orElseThrow().cell());

        // Без клетки пленного не отпускает проверка положения.
        platform.tick(200);
        assertTrue(captivity.isCaptive(outside));

        List<String> list = context.run(Optional.empty(), "list");
        assertEquals("command.list.header 1", list.get(0));
        assertEquals("command.list.entry Outside player command.list.no_cell 100", list.get(1));

        assertEquals(List.of("command.released Outside"), context.run(Optional.empty(), "release Outside"));
        assertEquals(List.of("command.not_captive Outside"), context.run(Optional.empty(), "release Outside"));
        assertEquals(List.of("command.target_not_found Nobody"), context.run(Optional.empty(), "capture Nobody"));
        assertEquals(List.of("command.no_permission"), context.run(Optional.of(captor), "capture Outside"));
    }

    @Test
    void captivityIsRestoredAfterRestart() {
        interact(captor, prisoner, LEAD);
        InMemoryStorage storage = context.storage;

        context = start(storage);
        assertTrue(captivity.isCaptive(prisoner));

        // Клетка вычисляется заново по опорному блоку, поэтому выход из неё по-прежнему освобождает.
        platform.move(prisoner, new Location(WORLD, 10, 1, 10));
        platform.tick(20);
        assertFalse(captivity.isCaptive(prisoner));
    }

    @Test
    void commandRootIgnoresSlashAndNamespace() {
        assertEquals("tell", Restrictions.root("/minecraft:tell Steve hi"));
        assertEquals("spawn", Restrictions.root("Spawn"));
    }
}
