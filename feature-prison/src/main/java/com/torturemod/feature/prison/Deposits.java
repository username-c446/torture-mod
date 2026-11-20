package com.torturemod.feature.prison;

import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Menus;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.PrisonCouriers;
import com.torturemod.api.prison.PrisonRight;
import com.torturemod.api.resistance.ResistanceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Норма труда: пленный открывает сундук сдачи, сданное засчитывается ему и переносится в сундук.
 * Норма своя у пленного, иначе у зоны сундука, иначе у тюрьмы. Выполнил за игровой день — награда.
 */
final class Deposits {
    private final Support s;
    private final ResistanceService resistance;

    Deposits(Support support, ResistanceService resistance) {
        this.s = support;
        this.resistance = resistance;
    }

    void onBlockUse(GameEvent.BlockInteracted event) {
        if (event.cancellation().isCancelled()) {
            return;
        }
        Optional<Prison> prison = s.registry.ofInmate(event.actor());
        if (prison.isEmpty() || prison.get().chests.get(event.block()) != ChestRole.DEPOSIT) {
            return;
        }
        event.cancellation().cancel();
        Inmate inmate = s.registry.inmates.get(event.actor());
        Map<String, Integer> quota = quotaFor(inmate, prison.get(), event.block());
        String title = s.text("prison.deposit.title", progress(inmate, quota));
        s.context.platform().menus().openStorage(event.actor(), new Menus.StorageMenu(title, 3, List.of(),
                stacks -> deposit(event.actor(), prison.get().id, event.block(), stacks)));
        s.hint(event.actor(), "hint.prison.deposit");
    }

    private void deposit(TargetId captive, String prisonId, BlockLocation chest, List<ItemStackData> stacks) {
        Inmate inmate = s.registry.inmates.get(captive);
        Optional<Prison> prison = s.registry.byId(prisonId);
        if (inmate == null || prison.isEmpty() || !inmate.prisonId.equals(prisonId) || stacks.isEmpty()) {
            s.giveBack(captive, stacks);
            return;
        }
        List<ItemStackData> left = s.context.platform().containers().insert(chest, stacks);
        s.giveBack(captive, left);
        Map<String, Integer> stored = count(stacks);
        count(left).forEach((id, n) -> stored.merge(id, -n, Integer::sum));
        Map<String, Integer> quota = quotaFor(inmate, prison.get(), chest);
        stored.forEach((id, n) -> {
            if (n > 0 && quota.containsKey(id)) {
                inmate.progress.merge(id, n, Integer::sum);
            }
        });
        if (!left.isEmpty()) {
            s.message(captive, "prison.deposit.chest_full");
        }
        s.message(captive, "prison.deposit.progress", progress(inmate, quota));
        if (!inmate.completedToday && !quota.isEmpty() && done(inmate, quota)) {
            inmate.completedToday = true;
            reward(captive, inmate, prison.get());
        }
        s.registry.saveInmate(captive);
    }

    /** Норма пленного: своя, иначе зоны сундука, иначе тюрьмы. */
    Map<String, Integer> quotaFor(Inmate inmate, Prison prison, BlockLocation chest) {
        if (!inmate.quotaOverride.isEmpty()) {
            return inmate.quotaOverride;
        }
        Optional<Zone> zone = prison.zoneAt(chest).filter(found -> !found.quota.isEmpty());
        return zone.map(found -> found.quota).orElse(prison.quota);
    }

    String progress(Inmate inmate, Map<String, Integer> quota) {
        if (quota.isEmpty()) {
            return s.text("prison.quota.none");
        }
        List<String> parts = new ArrayList<>();
        quota.forEach((id, need) -> parts.add(Math.min(need, inmate.progress.getOrDefault(id, 0)) + "/" + need + " "
                + Support.shortId(id)));
        return String.join(", ", parts) + (inmate.completedToday ? " ✔" : "");
    }

    private static boolean done(Inmate inmate, Map<String, Integer> quota) {
        return quota.entrySet().stream().allMatch(entry -> inmate.progress.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    /** Награда тюрьмы: сокращение срока, быстрое восстановление и вещи, которые кто-то должен принести. */
    private void reward(TargetId captive, Inmate inmate, Prison prison) {
        List<String> parts = new ArrayList<>();
        if (prison.rewardSentencePercent > 0 && inmate.hasSentence()) {
            long cut = inmate.sentenceLeftMillis * prison.rewardSentencePercent / 100;
            inmate.sentenceLeftMillis = Math.max(1, inmate.sentenceLeftMillis - cut);
            parts.add(s.text("prison.reward.sentence", prison.rewardSentencePercent));
        }
        if (prison.rewardRecoveryMultiplier != 1) {
            resistance.boostRecovery(captive, prison.rewardRecoveryMultiplier, s.settings().rewardRecoveryMillis());
            parts.add(s.text("prison.reward.recovery", prison.rewardRecoveryMultiplier));
        }
        if (!prison.rewardItems.isEmpty()) {
            parts.add(s.text("prison.reward.items", s.items(prison.rewardItems)));
            deliver(captive, prison);
        }
        s.message(captive, "prison.quota.done", parts.isEmpty() ? s.text("prison.reward.none") : String.join("; ", parts));
        s.notifyTeam(prison, PrisonRight.QUOTA, "prison.quota.done_team", s.name(captive));
    }

    /** Вещи не телепортируются: их несёт курьер, а без него команда получает просьбу отнести их. */
    private void deliver(TargetId captive, Prison prison) {
        List<BlockLocation> chests = prison.chests(ChestRole.REWARDS);
        List<ItemStackData> items = new ArrayList<>();
        prison.rewardItems.forEach((id, count) -> items.add(ItemStackData.of(id, count)));
        boolean courier = !chests.isEmpty() && s.context.services().find(PrisonCouriers.class)
                .filter(couriers -> couriers.deliver(prison.id, captive, chests.get(0), items)).isPresent();
        if (!courier) {
            String where = chests.isEmpty() ? s.text("prison.reward.no_chest")
                    : chests.get(0).x() + " " + chests.get(0).y() + " " + chests.get(0).z();
            s.notifyTeam(prison, PrisonRight.QUOTA, "prison.reward.carry", s.name(captive), s.items(prison.rewardItems), where);
        }
    }

    /** Игровой день нормы идёт, пока пленный онлайн. @return изменились ли условия */
    boolean advanceDay(TargetId captive, Inmate inmate) {
        inmate.dayElapsedMillis += Warden.CHECK_INTERVAL_TICKS * 50L;
        if (inmate.dayElapsedMillis < s.settings().dayMillis()) {
            return true;
        }
        inmate.dayElapsedMillis = 0;
        boolean hadProgress = !inmate.progress.isEmpty() || inmate.completedToday;
        inmate.progress.clear();
        inmate.completedToday = false;
        if (hadProgress) {
            s.message(captive, "prison.quota.new_day");
        }
        s.registry.saveInmate(captive);
        return true;
    }

    private static Map<String, Integer> count(List<ItemStackData> stacks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        stacks.forEach(stack -> counts.merge(stack.itemId(), stack.count(), Integer::sum));
        return counts;
    }
}
