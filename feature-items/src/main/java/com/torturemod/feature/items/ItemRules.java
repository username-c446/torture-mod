package com.torturemod.feature.items;

import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.model.ItemTraits;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Правила конфига. match: ID предмета, #тег или @мод. Точное правило побеждает:
 * предмет, затем тег, затем мод. При равной точности побеждает первое в файле.
 */
final class ItemRules {
    record Rule(String match, Optional<Set<String>> types, OptionalDouble strength, boolean disabled) {
        String describe() {
            return "rule " + match;
        }
    }

    private final List<Rule> rules;

    private ItemRules(List<Rule> rules) {
        this.rules = rules;
    }

    static ItemRules from(ConfigView config) {
        return new ItemRules(config.getSections("items.rules").stream()
                .filter(section -> section.has("match"))
                .map(section -> new Rule(
                        section.getString("match", ""),
                        section.has("types") ? Optional.of(Set.copyOf(section.getStringList("types"))) : Optional.empty(),
                        section.has("strength") ? OptionalDouble.of(section.getDouble("strength", 1)) : OptionalDouble.empty(),
                        section.getBoolean("disabled", false)))
                .toList());
    }

    Optional<Rule> find(String itemId, Optional<ItemTraits> traits) {
        Optional<Rule> exact = rules.stream().filter(rule -> rule.match().equals(itemId)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        Set<String> tags = traits.map(ItemTraits::tags).orElse(Set.of());
        Optional<Rule> tagged = rules.stream()
                .filter(rule -> rule.match().startsWith("#") && tags.contains(rule.match().substring(1)))
                .findFirst();
        if (tagged.isPresent()) {
            return tagged;
        }
        String namespace = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "minecraft";
        return rules.stream().filter(rule -> rule.match().equals("@" + namespace)).findFirst();
    }
}
