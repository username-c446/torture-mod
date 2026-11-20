package com.torturemod.feature.prison;

import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Menus;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.PrisonRight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Выкуп через сундук выкупа: меню со списком пленных и ценами, оплата предметами. Если тюрьма
 * подтверждает сделки сама, оплата лежит залогом до ответа команды.
 */
final class Ransom {
    private static final int CHECK_INTERVAL_TICKS = 200;

    private final Support s;
    private long ticks;

    Ransom(Support support) {
        this.s = support;
    }

    void onBlockUse(GameEvent.BlockInteracted event) {
        if (event.cancellation().isCancelled() || s.captivity.isCaptive(event.actor())) {
            return;
        }
        Optional<Prison> prison = s.registry.at(event.block())
                .filter(found -> found.chests.get(event.block()) == ChestRole.RANSOM);
        if (prison.isEmpty()) {
            return;
        }
        // Команда открывает сундук как обычно и забирает выручку.
        if (s.registry.has(prison.get(), event.actor(), PrisonRight.DEALS)
                || s.registry.has(prison.get(), event.actor(), PrisonRight.MANAGE)) {
            return;
        }
        event.cancellation().cancel();
        open(event.actor(), prison.get(), event.block());
    }

    void open(TargetId buyer, Prison prison, BlockLocation chest) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (Map.Entry<TargetId, Inmate> entry : s.registry.inmates.entrySet()) {
            Inmate inmate = entry.getValue();
            if (!inmate.prisonId.equals(prison.id) || inmate.price.isEmpty() || slot >= 45) {
                continue;
            }
            TargetId captive = entry.getKey();
            boolean pending = s.registry.deals.values().stream()
                    .anyMatch(deal -> deal.captive.equals(captive) && deal.status == Deal.Status.PENDING);
            List<String> lore = new ArrayList<>(List.of(s.text("prison.ransom.price", s.items(inmate.price))));
            lore.add(s.text(pending ? "prison.ransom.pending" : "prison.ransom.click"));
            entries.add(new Menus.Entry(slot++, "minecraft:player_head", s.name(captive), lore,
                    pending ? null : () -> pay(buyer, prison.id, chest, captive)));
        }
        if (entries.isEmpty()) {
            entries.add(new Menus.Entry(22, "minecraft:barrier", s.text("prison.ransom.empty"), List.of(), null));
        }
        slot = 45;
        for (Deal deal : List.copyOf(s.registry.deals.values())) {
            if (!deal.buyer.equals(buyer) || !deal.prisonId.equals(prison.id) || slot >= 54) {
                continue;
            }
            boolean rejected = deal.status == Deal.Status.REJECTED;
            entries.add(new Menus.Entry(slot++, rejected ? "minecraft:chest" : "minecraft:clock",
                    s.text(rejected ? "prison.ransom.refund" : "prison.ransom.cancel", s.name(deal.captive)),
                    List.of(s.text("prison.ransom.escrow", stackList(deal.escrow))), () -> {
                        s.context.platform().menus().close(buyer);
                        refund(buyer, deal);
                    }));
        }
        s.context.platform().menus().open(buyer, new Menus.Menu(s.text("prison.ransom.title", prison.name), 6, entries));
        s.hint(buyer, "hint.prison.ransom");
    }

    private void pay(TargetId buyer, String prisonId, BlockLocation chest, TargetId captive) {
        s.context.platform().menus().close(buyer);
        Inmate inmate = s.registry.inmates.get(captive);
        if (inmate == null || inmate.price.isEmpty()) {
            return;
        }
        s.context.platform().menus().openStorage(buyer, new Menus.StorageMenu(
                s.text("prison.ransom.pay_title", s.name(captive), s.items(inmate.price)), 3, List.of(),
                stacks -> settle(buyer, prisonId, chest, captive, stacks)));
    }

    /** Проверяет оплату: цена уходит в сундук или в залог, остальное возвращается покупателю. */
    private void settle(TargetId buyer, String prisonId, BlockLocation chest, TargetId captive,
                        List<ItemStackData> stacks) {
        Inmate inmate = s.registry.inmates.get(captive);
        Optional<Prison> prison = s.registry.byId(prisonId);
        if (inmate == null || prison.isEmpty() || !inmate.prisonId.equals(prisonId) || inmate.price.isEmpty()) {
            s.giveBack(buyer, stacks);
            s.message(buyer, "prison.ransom.gone");
            return;
        }
        List<ItemStackData> rest = new ArrayList<>();
        List<ItemStackData> payment = take(stacks, inmate.price, rest);
        if (payment == null) {
            s.giveBack(buyer, stacks);
            s.message(buyer, "prison.ransom.not_enough", s.items(inmate.price));
            return;
        }
        s.giveBack(buyer, rest);
        if (prison.get().confirmDeals) {
            Deal deal = new Deal(UUID.randomUUID().toString(), prisonId, captive, buyer, payment,
                    s.context.clock().millis(), Deal.Status.PENDING);
            s.registry.saveDeal(deal);
            s.message(buyer, "prison.ransom.requested", s.name(captive));
            s.notifyTeam(prison.get(), PrisonRight.DEALS, "prison.ransom.request_team", s.name(buyer), s.name(captive));
            return;
        }
        if (!store(chest, payment)) {
            s.giveBack(buyer, payment);
            s.message(buyer, "prison.ransom.chest_full");
            return;
        }
        complete(prison.get(), captive, buyer);
    }

    void accept(TargetId actor, Deal deal) {
        Optional<Prison> prison = s.registry.byId(deal.prisonId);
        if (prison.isEmpty() || deal.status != Deal.Status.PENDING) {
            return;
        }
        if (!s.registry.ofInmate(deal.captive).map(found -> found.id).filter(deal.prisonId::equals).isPresent()) {
            reject(actor, deal);
            return;
        }
        List<BlockLocation> chests = prison.get().chests(ChestRole.RANSOM);
        if (chests.isEmpty() || !store(chests.get(0), deal.escrow)) {
            s.message(actor, "prison.ransom.chest_full");
            return;
        }
        s.registry.removeDeal(deal);
        complete(prison.get(), deal.captive, deal.buyer);
        s.message(actor, "prison.deal.accepted", s.name(deal.captive));
    }

    void reject(TargetId actor, Deal deal) {
        deal.status = Deal.Status.REJECTED;
        s.registry.saveDeal(deal);
        s.message(actor, "prison.deal.rejected", s.name(deal.captive));
        s.message(deal.buyer, "prison.ransom.rejected", s.name(deal.captive));
    }

    private void refund(TargetId buyer, Deal deal) {
        s.registry.removeDeal(deal);
        s.giveBack(buyer, deal.escrow);
        s.message(buyer, "prison.ransom.refunded");
    }

    private void complete(Prison prison, TargetId captive, TargetId buyer) {
        s.message(captive, "prison.ransom.you_are_free", s.name(buyer));
        s.message(buyer, "prison.ransom.done", s.name(captive));
        s.notifyTeam(prison, PrisonRight.DEALS, "prison.ransom.done_team", s.name(captive), s.name(buyer));
        s.captivity.release(captive, ReleaseReason.RANSOM);
    }

    /** Сделки, на которые не ответили за сутки, отклоняются: залог можно забрать. */
    void tick() {
        if (++ticks % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        long now = s.context.clock().millis();
        for (Deal deal : List.copyOf(s.registry.deals.values())) {
            if (deal.status == Deal.Status.PENDING && now - deal.createdAt > s.settings().dealExpiryMillis()) {
                deal.status = Deal.Status.REJECTED;
                s.registry.saveDeal(deal);
                s.message(deal.buyer, "prison.ransom.expired", s.name(deal.captive));
            }
        }
    }

    /** Кладёт оплату в сундук целиком или не кладёт ничего. */
    private boolean store(BlockLocation chest, List<ItemStackData> payment) {
        List<ItemStackData> left = s.context.platform().containers().insert(chest, payment);
        if (left.isEmpty()) {
            return true;
        }
        Map<String, Integer> inserted = new LinkedHashMap<>();
        payment.forEach(stack -> inserted.merge(stack.itemId(), stack.count(), Integer::sum));
        left.forEach(stack -> inserted.merge(stack.itemId(), -stack.count(), Integer::sum));
        inserted.forEach((id, count) -> {
            if (count > 0) {
                s.context.platform().containers().remove(chest, id, count);
            }
        });
        return false;
    }

    /**
     * Отделяет цену от положенного. @param rest сюда попадает сдача
     * @return стопки на сумму цены; null, если не хватает
     */
    static List<ItemStackData> take(List<ItemStackData> stacks, Map<String, Integer> price, List<ItemStackData> rest) {
        Map<String, Integer> need = new LinkedHashMap<>(price);
        List<ItemStackData> payment = new ArrayList<>();
        for (ItemStackData stack : stacks) {
            int want = need.getOrDefault(stack.itemId(), 0);
            if (want <= 0) {
                rest.add(stack);
                continue;
            }
            int used = Math.min(want, stack.count());
            payment.add(stack.withCount(used));
            need.put(stack.itemId(), want - used);
            if (used < stack.count()) {
                rest.add(stack.withCount(stack.count() - used));
            }
        }
        return need.values().stream().allMatch(left -> left <= 0) ? payment : null;
    }

    private String stackList(List<ItemStackData> stacks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        stacks.forEach(stack -> counts.merge(stack.itemId(), stack.count(), Integer::sum));
        return s.items(counts);
    }
}
