package com.torturemod.feature.items;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.items.Classification;
import com.torturemod.api.items.ItemClassifier;
import com.torturemod.api.model.ItemTraits;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Правила конфига поверх автоопределения. Результат кэшируется до перезагрузки конфига. */
final class RuleClassifier implements ItemClassifier {
    private final FeatureContext context;
    private final Map<String, Classification> cache = new ConcurrentHashMap<>();
    private volatile ItemRules rules;
    private volatile boolean nameFallback;

    RuleClassifier(FeatureContext context) {
        this.context = context;
        reload();
    }

    void reload() {
        rules = ItemRules.from(context.config());
        nameFallback = context.config().getBoolean("items.name_fallback", true);
        cache.clear();
    }

    @Override
    public Classification classify(String itemId) {
        return cache.computeIfAbsent(itemId, this::compute);
    }

    private Classification compute(String itemId) {
        Optional<ItemTraits> traits = context.platform().items().traits(itemId);
        Optional<ItemRules.Rule> rule = rules.find(itemId, traits);
        if (rule.isPresent() && rule.get().disabled()) {
            return new Classification(itemId, Set.of(), 0, rule.get().describe() + " (disabled)");
        }
        AutoDetection.Result auto = traits.map(found -> AutoDetection.detect(found, nameFallback))
                .orElse(new AutoDetection.Result(Set.of(), 1.0, "unknown item"));
        if (rule.isEmpty()) {
            return new Classification(itemId, auto.types(), auto.strength(), auto.reason());
        }
        Set<String> types = rule.get().types().orElse(auto.types());
        double strength = rule.get().strength().orElse(auto.strength());
        return new Classification(itemId, types, strength, rule.get().describe());
    }
}
