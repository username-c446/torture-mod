package com.torturemod.feature.minions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.prison.EscapeHelped;
import com.torturemod.api.prison.PrisonCouriers;
import com.torturemod.api.prison.PrisonEscaped;
import com.torturemod.api.prison.PrisonGuards;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.cuffs.CuffsFeature;
import com.torturemod.feature.prison.PrisonFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class MinionsFeatureTest {
    private static final String W = "w";
    private static final String STICK = "minecraft:stick";
    private static final String EYE = "minecraft:ender_eye";

    private FakePlatform platform;
    private FakeContext context;
    private CaptivityService captivity;
    private ResistanceService resistance;
    private PrisonService prisons;
    private TargetId owner;
    private TargetId stranger;
    private String prisonId;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        owner = platform.player("Owner", new Location(W, 5, 1, 5));
        stranger = platform.player("Stranger", new Location(W, 12, 1, 12));
        context = new FakeContext(platform);
        new CaptivityFeature().start(context);
        new ResistanceFeature().start(context);
        new CuffsFeature().start(context);
        new PrisonFeature().start(context);
        new MinionsFeature(() -> 0.5).start(context);
        captivity = context.services().find(CaptivityService.class).orElseThrow();
        resistance = context.services().find(ResistanceService.class).orElseThrow();
        prisons = context.services().find(PrisonService.class).orElseThrow();
        platform.fire(new GameEvent.BlockAttacked(owner, new BlockLocation(W, 0, 0, 0), Optional.of(STICK), false,
                new Cancellation()));
        platform.fire(new GameEvent.BlockInteracted(owner, new BlockLocation(W, 30, 10, 30), Optional.of(STICK), false,
                new Cancellation()));
        platform.tick(1);
        platform.fire(new GameEvent.ItemUsed(owner, STICK, true, new Cancellation()));
        prisonId = prisons.prisons().iterator().next().id();
    }

    private Cancellation interact(TargetId actor, TargetId target, String item, boolean sneaking) {
        Cancellation cancellation = new Cancellation();
        platform.fire(new GameEvent.EntityInteracted(actor, target, Optional.ofNullable(item), sneaking, cancellation));
        return cancellation;
    }

    private TargetId brokenMob(String type, Location where) {
        TargetId mob = platform.mob(type, where);
        captivity.captureWithoutCell(mob, Optional.of(owner));
        resistance.breakNow(mob);
        return mob;
    }

    private TargetId enlistedZombie() {
        TargetId zombie = brokenMob("minecraft:zombie", new Location(W, 10, 1, 10));
        interact(owner, zombie, EYE, false);
        return zombie;
    }

    @Test
    void eyeOfEnderEnlistsBrokenMob() {
        TargetId zombie = enlistedZombie();
        assertFalse(captivity.isCaptive(zombie));
        assertEquals(ReleaseReason.SERVICE, context.published(CaptiveReleased.class).get(0).reason());
        assertTrue(platform.enslaved.contains(zombie));
        assertTrue(platform.consumed.contains(owner.value() + "|" + EYE + "|1"));
        assertTrue(platform.labels.get(zombie).startsWith("minions.label.POST"));
    }

    @Test
    void unbrokenMobIsNotEnlisted() {
        TargetId zombie = platform.mob("minecraft:zombie", new Location(W, 10, 1, 10));
        captivity.captureWithoutCell(zombie, Optional.of(owner));
        interact(owner, zombie, EYE, false);
        assertTrue(captivity.isCaptive(zombie));
        assertFalse(platform.enslaved.contains(zombie));
    }

    @Test
    void strangerCannotEnlist() {
        TargetId zombie = brokenMob("minecraft:zombie", new Location(W, 10, 1, 10));
        interact(stranger, zombie, EYE, false);
        assertTrue(captivity.isCaptive(zombie));
    }

    @Test
    void brokenGolemServesByItself() {
        TargetId golem = brokenMob("minecraft:iron_golem", new Location(W, 10, 1, 10));
        assertFalse(captivity.isCaptive(golem));
        assertTrue(platform.enslaved.contains(golem));
    }

    @Test
    void guardsAttackVisibleEscapeeButNotFriends() {
        TargetId zombie = enlistedZombie();
        TargetId escapee = platform.player("Escapee", new Location(W, 20, 1, 20));
        context.bus().publish(new PrisonEscaped(prisonId, escapee, new Location(W, 20, 1, 20), true));
        platform.tick(10);
        assertEquals(escapee, platform.attackTargets.get(zombie));
        assertTrue(context.services().find(PrisonGuards.class).orElseThrow().anyGuardSees(prisonId, escapee, 24));

        platform.hiddenPairs.add(zombie.value() + "|" + escapee.value());
        platform.tick(10);
        assertNull(platform.attackTargets.get(zombie));

        context.bus().publish(new EscapeHelped(prisonId, owner, escapee));
        platform.tick(10);
        assertNull(platform.attackTargets.get(zombie));
    }

    @Test
    void helperIsAttacked() {
        TargetId zombie = enlistedZombie();
        context.bus().publish(new EscapeHelped(prisonId, stranger, stranger));
        platform.tick(10);
        assertEquals(stranger, platform.attackTargets.get(zombie));
    }

    @Test
    void sneakClickCyclesModeAndFollowerWalksToOwner() {
        TargetId zombie = enlistedZombie();
        assertTrue(interact(owner, zombie, null, true).isCancelled());
        platform.move(owner, new Location(W, 25, 1, 25));
        platform.tick(10);
        assertEquals(new Location(W, 25, 1, 25), platform.moves.get(zombie));
    }

    @Test
    void courierCarriesRewardFromChestToCaptive() {
        TargetId zombie = enlistedZombie();
        interact(owner, zombie, null, true);
        interact(owner, zombie, null, true);
        BlockLocation chest = new BlockLocation(W, 20, 0, 20);
        platform.chests.put(chest, new ArrayList<>(List.of(ItemStackData.of("minecraft:bread", 5))));
        TargetId prisoner = platform.player("Prisoner", new Location(W, 8, 1, 8));
        captivity.captureWithoutCell(prisoner, Optional.of(owner));
        PrisonCouriers couriers = context.services().find(PrisonCouriers.class).orElseThrow();
        assertTrue(couriers.deliver(prisonId, prisoner, chest, List.of(ItemStackData.of("minecraft:bread", 2))));
        platform.tick(10);
        assertEquals(20.5, platform.moves.get(zombie).x());
        platform.move(zombie, new Location(W, 20.5, 0, 20.5));
        platform.tick(10);
        assertEquals("minecraft:bread", platform.held.get(zombie).itemId());
        assertEquals(3, platform.countIn(chest, "minecraft:bread"));
        platform.move(zombie, new Location(W, 9, 1, 8));
        platform.tick(10);
        assertEquals(2, platform.givenTo(prisoner).get(0).count());
        assertNull(platform.held.get(zombie));
    }

    @Test
    void noCourierMeansTeamCarries() {
        TargetId prisoner = platform.player("Prisoner", new Location(W, 8, 1, 8));
        PrisonCouriers couriers = context.services().find(PrisonCouriers.class).orElseThrow();
        assertFalse(couriers.deliver(prisonId, prisoner, new BlockLocation(W, 20, 0, 20), List.of()));
    }

    @Test
    void equipmentFromHand() {
        TargetId zombie = enlistedZombie();
        platform.give(owner, 0, "minecraft:iron_helmet");
        assertTrue(interact(owner, zombie, "minecraft:iron_helmet", false).isCancelled());
        assertEquals("minecraft:iron_helmet", platform.equipment.get(zombie).get("head"));
    }

    @Test
    void villagerTradesCheaperAndFarmerStoresCrops() {
        TargetId villager = brokenMob("minecraft:villager", new Location(W, 10, 1, 10));
        interact(owner, villager, EYE, false);
        interact(owner, villager, null, false);
        assertEquals(List.of(villager.value() + "|" + owner.value()), platform.befriended);

        platform.professions.put(villager, "minecraft:farmer");
        platform.mobInventories.put(villager, new ArrayList<>(List.of(ItemStackData.of("minecraft:wheat", 20),
                ItemStackData.of("minecraft:carrot", 20))));
        BlockLocation chest = new BlockLocation(W, 11, 0, 10);
        platform.setBlock(W, 11, 0, 10, "minecraft:chest");
        platform.chests.put(chest, new ArrayList<>());
        platform.fire(new GameEvent.BlockInteracted(owner, chest, Optional.of(STICK), true, new Cancellation()));
        platform.tick(10);
        assertEquals(20, platform.countIn(chest, "minecraft:wheat"));
        assertEquals(4, platform.countIn(chest, "minecraft:carrot"));
    }

    @Test
    void minionsSurviveRestart() {
        TargetId zombie = enlistedZombie();
        FakePlatform restartedPlatform = new FakePlatform();
        restartedPlatform.targets.put(zombie, platform.targets.get(zombie));
        FakeContext restarted = new FakeContext(restartedPlatform, context.storage);
        new CaptivityFeature().start(restarted);
        new ResistanceFeature().start(restarted);
        new CuffsFeature().start(restarted);
        new PrisonFeature().start(restarted);
        new MinionsFeature(() -> 0.5).start(restarted);
        restartedPlatform.tick(10);
        assertTrue(restartedPlatform.enslaved.contains(zombie));
    }
}
