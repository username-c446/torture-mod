package com.torturemod.feature.prison;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.help.Hints;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.prison.PrisonRight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Общее для частей фичи: контекст, реестр, тексты, имена и списки предметов. */
final class Support {
    final FeatureContext context;
    final PrisonRegistry registry;
    final CaptivityService captivity;
    private final Supplier<PrisonSettings> settings;

    Support(FeatureContext context, PrisonRegistry registry, CaptivityService captivity,
            Supplier<PrisonSettings> settings) {
        this.context = context;
        this.registry = registry;
        this.captivity = captivity;
        this.settings = settings;
    }

    PrisonSettings settings() {
        return settings.get();
    }

    String text(String key, Object... args) {
        return context.messages().text(key, args);
    }

    void message(TargetId player, String key, Object... args) {
        context.platform().feedback().message(player, text(key, args));
    }

    void bar(TargetId player, String key, Object... args) {
        context.platform().feedback().actionBar(player, text(key, args));
    }

    void hint(TargetId player, String key, Object... args) {
        context.services().find(Hints.class).ifPresent(hints -> hints.hint(player, key, args));
    }

    String name(TargetId id) {
        return context.platform().targets().state(id).map(TargetState::name)
                .or(() -> context.platform().targets().playerName(id)).orElse(id.value().toString().substring(0, 8));
    }

    String roleName(Role role) {
        return role.preset ? text("prison.role." + role.id) : role.name;
    }

    String rightName(PrisonRight right) {
        return text("prison.right." + right.name());
    }

    /** Сообщение всем в сети, у кого в тюрьме есть это право. */
    void notifyTeam(Prison prison, PrisonRight right, String key, Object... args) {
        for (TargetState player : context.platform().players().online()) {
            if (registry.has(prison, player.id(), right)) {
                message(player.id(), key, args);
            }
        }
    }

    /** «64 cobblestone, 3 iron_ingot». */
    String items(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return text("prison.items.none");
        }
        return counts.entrySet().stream().map(entry -> entry.getValue() + " " + shortId(entry.getKey()))
                .collect(Collectors.joining(", "));
    }

    static String shortId(String itemId) {
        int colon = itemId.indexOf(':');
        return colon >= 0 && itemId.startsWith("minecraft:") ? itemId.substring(colon + 1) : itemId;
    }

    /** Образцы из меню: ID предмета → сумма по всем стопкам, не больше пределов из конфига. */
    Map<String, Integer> samples(List<ItemStackData> stacks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStackData stack : stacks) {
            if (counts.size() >= settings().quotaMaxKinds() && !counts.containsKey(stack.itemId())) {
                continue;
            }
            counts.merge(stack.itemId(), stack.count(), Integer::sum);
        }
        counts.replaceAll((id, count) -> Math.min(count, settings().quotaMaxCount()));
        return counts;
    }

    /** Возвращает игроку образцы, которые он клал в меню, чтобы задать список. */
    void giveBack(TargetId player, List<ItemStackData> stacks) {
        if (!stacks.isEmpty()) {
            context.platform().inventories().give(player, new ArrayList<>(stacks));
        }
    }

    static String minutes(long millis) {
        return String.valueOf((millis + 59_999) / 60_000);
    }
}
