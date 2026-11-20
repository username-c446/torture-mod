package com.torturemod.feature.prison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.prison.ChestRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class QuotaAndRansomTest extends PrisonTestBase {
    private static final String COBBLE = "minecraft:cobblestone";
    private static final String GOLD = "minecraft:gold_ingot";

    @BeforeEach
    void setUp() {
        setUpPrison();
        captureInside();
        cuffs.uncuff(prisoner);
    }

    private Prison model() {
        return registry.byId(prison().id()).orElseThrow();
    }

    @Test
    void chestRolesCycleWithStick() {
        BlockLocation chest = chest(20, 20);
        designate(chest, 1);
        assertEquals(ChestRole.DEPOSIT, model().chests.get(chest));
        designate(chest, 3);
        assertEquals(ChestRole.WAREHOUSE, model().chests.get(chest));
        designate(chest, 1);
        assertFalse(model().chests.containsKey(chest));
    }

    @Test
    void depositCountsTowardQuotaAndRewards() {
        BlockLocation deposit = chest(20, 20);
        designate(deposit, 1);
        BlockLocation rewards = chest(22, 20);
        designate(rewards, 2);
        openConsole(owner);
        click(owner, 13);
        click(owner, 10);
        platform.closeStorage(owner, items(COBBLE, 10));
        assertEquals(Map.of(COBBLE, 10), model().quota);
        assertEquals(COBBLE, platform.givenTo(owner).get(0).itemId());
        click(owner, 15);
        click(owner, 15);
        click(owner, 23);
        platform.closeStorage(owner, items("minecraft:bread", 2));
        assertEquals(10, model().rewardSentencePercent);
        registry.inmates.get(prisoner).sentenceLeftMillis = 60 * 60_000L;

        assertTrue(use(prisoner, deposit, null, false).isCancelled());
        assertTrue(platform.openStorage.containsKey(prisoner));
        platform.closeStorage(prisoner, List.of(ItemStackData.of(COBBLE, 6), ItemStackData.of("minecraft:dirt", 1)));
        assertFalse(registry.inmates.get(prisoner).completedToday);
        use(prisoner, deposit, null, false);
        platform.closeStorage(prisoner, items(COBBLE, 4));
        assertTrue(registry.inmates.get(prisoner).completedToday);
        assertEquals(10, platform.countIn(deposit, COBBLE));
        assertEquals(54 * 60_000L, registry.inmates.get(prisoner).sentenceLeftMillis);
        assertTrue(messaged(prisoner, "prison.quota.done"));
        assertTrue(messaged(owner, "prison.reward.carry"));
    }

    @Test
    void quotaResetsOnNewGameDay() {
        BlockLocation deposit = chest(20, 20);
        designate(deposit, 1);
        model().quota.put(COBBLE, 1);
        use(prisoner, deposit, null, false);
        platform.closeStorage(prisoner, items(COBBLE, 1));
        assertTrue(registry.inmates.get(prisoner).completedToday);
        platform.tick(20 * 60 * 20 + 20);
        assertFalse(registry.inmates.get(prisoner).completedToday);
        assertTrue(registry.inmates.get(prisoner).progress.isEmpty());
    }

    private BlockLocation ransomChest() {
        BlockLocation chest = chest(24, 20);
        designate(chest, 3);
        return chest;
    }

    private void setPrice() {
        openConsole(owner);
        click(owner, 10);
        click(owner, 0);
        click(owner, 19);
        platform.closeStorage(owner, items(GOLD, 3));
        assertEquals(Map.of(GOLD, 3), registry.inmates.get(prisoner).price);
    }

    @Test
    void ransomPaysIntoChestAndFrees() {
        BlockLocation chest = ransomChest();
        setPrice();
        assertTrue(use(stranger, chest, null, false).isCancelled());
        click(stranger, 0);
        platform.closeStorage(stranger, List.of(ItemStackData.of(GOLD, 5), ItemStackData.of("minecraft:dirt", 1)));
        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(ReleaseReason.RANSOM, context.published(CaptiveReleased.class).get(0).reason());
        assertEquals(3, platform.countIn(chest, GOLD));
        int change = platform.givenTo(stranger).stream().filter(s -> s.itemId().equals(GOLD)).mapToInt(ItemStackData::count).sum();
        assertEquals(2, change);
    }

    @Test
    void notEnoughIsReturned() {
        BlockLocation chest = ransomChest();
        setPrice();
        use(stranger, chest, null, false);
        click(stranger, 0);
        platform.closeStorage(stranger, items(GOLD, 2));
        assertTrue(captivity.isCaptive(prisoner));
        assertEquals(2, platform.givenTo(stranger).get(0).count());
    }

    @Test
    void confirmedDealWaitsForTeamThenFrees() {
        BlockLocation chest = ransomChest();
        setPrice();
        model().confirmDeals = true;
        use(stranger, chest, null, false);
        click(stranger, 0);
        platform.closeStorage(stranger, items(GOLD, 3));
        assertTrue(captivity.isCaptive(prisoner));
        assertEquals(1, registry.deals.size());
        assertTrue(messaged(owner, "prison.ransom.request_team"));
        openConsole(owner);
        click(owner, 14);
        click(owner, 0);
        click(owner, 11);
        assertFalse(captivity.isCaptive(prisoner));
        assertEquals(3, platform.countIn(chest, GOLD));
        assertTrue(registry.deals.isEmpty());
    }

    @Test
    void rejectedDealDepositIsTakenBackAtChest() {
        BlockLocation chest = ransomChest();
        setPrice();
        model().confirmDeals = true;
        use(stranger, chest, null, false);
        click(stranger, 0);
        platform.closeStorage(stranger, items(GOLD, 3));
        openConsole(owner);
        click(owner, 14);
        click(owner, 0);
        click(owner, 15);
        assertTrue(captivity.isCaptive(prisoner));
        use(stranger, chest, null, false);
        click(stranger, 45);
        assertEquals(3, platform.givenTo(stranger).get(0).count());
        assertTrue(registry.deals.isEmpty());
    }

    @Test
    void teamOpensRansomChestNormally() {
        BlockLocation chest = ransomChest();
        assertFalse(use(owner, chest, null, false).isCancelled());
    }
}
