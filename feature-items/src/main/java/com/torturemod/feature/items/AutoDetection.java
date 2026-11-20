package com.torturemod.feature.items;

import com.torturemod.api.items.TortureTypes;
import com.torturemod.api.model.ItemTraits;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Автоопределение по признакам предмета: категория из класса, теги, известные ID и, если ничего не нашлось,
 * целые слова в ID. Работает и для модовых предметов, у которых платформа сумела вывести категорию.
 */
final class AutoDetection {
    record Result(Set<String> types, double strength, String reason) {
    }

    private static final Map<String, Set<String>> KINDS = Map.of(
            TortureTypes.STRIKE, Set.of("sword", "axe", "pickaxe", "shovel", "hoe", "trident", "tool", "weapon"),
            TortureTypes.FIRE, Set.of("igniter", "lava_bucket"),
            TortureTypes.DROWN, Set.of("water_bucket"),
            TortureTypes.POISON, Set.of("potion", "harmful_food"));
    private static final Map<String, Set<String>> TAGS = Map.of(
            TortureTypes.STRIKE, Set.of("minecraft:swords", "minecraft:axes", "minecraft:pickaxes", "minecraft:shovels",
                    "minecraft:hoes", "forge:tools", "c:tools"));
    private static final Map<String, Set<String>> IDS = Map.of(
            TortureTypes.FIRE, Set.of("minecraft:torch", "minecraft:soul_torch", "minecraft:blaze_rod",
                    "minecraft:blaze_powder", "minecraft:magma_cream"),
            TortureTypes.POISON, Set.of("minecraft:spider_eye", "minecraft:fermented_spider_eye",
                    "minecraft:poisonous_potato", "minecraft:pufferfish", "minecraft:rotten_flesh"));
    private static final Map<String, Set<String>> WORDS = Map.of(
            TortureTypes.STRIKE, Set.of("knife", "dagger", "blade", "sword", "axe", "hammer", "mace", "club", "whip",
                    "saw", "sickle", "spear", "cleaver", "machete", "katana"),
            TortureTypes.FIRE, Set.of("torch", "fire", "flame", "burner", "lava", "brand", "igniter"),
            TortureTypes.DROWN, Set.of("water"),
            TortureTypes.POISON, Set.of("poison", "venom", "toxic", "toxin"));

    private AutoDetection() {
    }

    static Result detect(ItemTraits traits, boolean nameFallback) {
        Set<String> types = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();
        for (String type : TortureTypes.ALL) {
            for (String kind : traits.kinds()) {
                if (KINDS.getOrDefault(type, Set.of()).contains(kind)) {
                    types.add(type);
                    reasons.add("kind " + kind);
                }
            }
            for (String tag : traits.tags()) {
                if (TAGS.getOrDefault(type, Set.of()).contains(tag)) {
                    types.add(type);
                    reasons.add("tag #" + tag);
                }
            }
            if (IDS.getOrDefault(type, Set.of()).contains(traits.itemId())) {
                types.add(type);
                reasons.add("id");
            }
        }
        if (types.isEmpty() && nameFallback) {
            String path = traits.itemId().substring(traits.itemId().indexOf(':') + 1);
            for (String word : path.split("[_/.\\-]")) {
                for (String type : TortureTypes.ALL) {
                    if (WORDS.getOrDefault(type, Set.of()).contains(word)) {
                        types.add(type);
                        reasons.add("name " + word);
                    }
                }
            }
        }
        double strength = types.contains(TortureTypes.STRIKE) ? strikeStrength(traits.attackDamage()) : 1.0;
        String reason = reasons.isEmpty() ? "auto: nothing" : "auto: " + String.join(", ", new LinkedHashSet<>(reasons));
        return new Result(Set.copyOf(types), strength, reason);
    }

    /** Урон голой рукой равен 1, железный меч около 6: сила растёт с уроном, но в разумных пределах. */
    static double strikeStrength(double attackDamage) {
        return Math.max(0.5, Math.min(2.5, attackDamage / 4));
    }
}
