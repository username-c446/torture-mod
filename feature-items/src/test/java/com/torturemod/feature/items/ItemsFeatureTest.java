package com.torturemod.feature.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.items.Classification;
import com.torturemod.api.items.ItemClassifier;
import com.torturemod.api.model.ItemTraits;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ItemsFeatureTest {
    private FakePlatform platform;
    private FakeContext context;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform();
        traits("minecraft:iron_sword", Set.of("minecraft:swords"), Set.of("sword"), 6);
        traits("minecraft:flint_and_steel", Set.of(), Set.of("igniter"), 1);
        traits("minecraft:water_bucket", Set.of(), Set.of("water_bucket"), 1);
        traits("minecraft:spider_eye", Set.of(), Set.of("harmful_food"), 1);
        traits("minecraft:torch", Set.of(), Set.of(), 1);
        traits("minecraft:stick", Set.of(), Set.of(), 1);
        traits("examplemod:steel_knife", Set.of(), Set.of(), 1);
        traits("examplemod:torchflower_seeds", Set.of(), Set.of(), 1);
        traits("examplemod:flame_sword", Set.of("minecraft:swords"), Set.of("sword"), 8);
        context = new FakeContext(platform);
    }

    private void traits(String id, Set<String> tags, Set<String> kinds, double damage) {
        platform.itemTraits.put(id, new ItemTraits(id, id.substring(0, id.indexOf(':')), tags, kinds, damage));
    }

    private ItemClassifier start() {
        new ItemsFeature().start(context);
        return context.services().find(ItemClassifier.class).orElseThrow();
    }

    @Test
    void detectsVanillaToolsByKindTagAndId() {
        ItemClassifier classifier = start();

        assertEquals(Set.of("strike"), classifier.classify("minecraft:iron_sword").types());
        assertEquals(1.5, classifier.classify("minecraft:iron_sword").strength());
        assertEquals(Set.of("fire"), classifier.classify("minecraft:flint_and_steel").types());
        assertEquals(Set.of("fire"), classifier.classify("minecraft:torch").types());
        assertEquals(Set.of("drown"), classifier.classify("minecraft:water_bucket").types());
        assertEquals(Set.of("poison"), classifier.classify("minecraft:spider_eye").types());
        assertTrue(classifier.classify("minecraft:stick").types().isEmpty());
        assertTrue(classifier.classify("minecraft:unknown").types().isEmpty());
    }

    @Test
    void nameIsOnlyAFallbackAndMatchesWholeWords() {
        ItemClassifier classifier = start();

        assertEquals(Set.of("strike"), classifier.classify("examplemod:steel_knife").types());
        // torchflower не содержит слова torch целиком.
        assertTrue(classifier.classify("examplemod:torchflower_seeds").types().isEmpty());
        // У меча есть категория, поэтому слово flame не добавляет огонь.
        assertEquals(Set.of("strike"), classifier.classify("examplemod:flame_sword").types());

        context.config.put("items.name_fallback", false);
        context = new FakeContext(platform);
        context.config.put("items.name_fallback", false);
        assertTrue(start().classify("examplemod:steel_knife").types().isEmpty());
    }

    @Test
    void rulesOverrideDetectionFromMostPrecise() {
        context.config.put("items.rules", List.of(
                Map.of("match", "@examplemod", "disabled", true),
                Map.of("match", "#minecraft:swords", "types", List.of("strike", "fire")),
                Map.of("match", "minecraft:stick", "types", List.of("strike"), "strength", 0.5),
                Map.of("match", "examplemod:flame_sword", "strength", 2.0)));
        ItemClassifier classifier = start();

        Classification stick = classifier.classify("minecraft:stick");
        assertEquals(Set.of("strike"), stick.types());
        assertEquals(0.5, stick.strength());
        assertEquals(Set.of("strike", "fire"), classifier.classify("minecraft:iron_sword").types());
        // Правило на предмет точнее правила на мод: тип остаётся автоматическим, сила из правила.
        Classification flameSword = classifier.classify("examplemod:flame_sword");
        assertEquals(Set.of("strike"), flameSword.types());
        assertEquals(2.0, flameSword.strength());
        assertTrue(classifier.classify("examplemod:steel_knife").types().isEmpty());
    }

    @Test
    void commandDescribesHeldItem() {
        start();
        var player = platform.player("Steve", new com.torturemod.api.model.Location("w", 0, 0, 0));
        platform.give(player, 0, "minecraft:iron_sword");

        assertEquals(List.of("items.classified minecraft:iron_sword strike 1.50 auto: kind sword, tag #minecraft:swords"),
                context.run(java.util.Optional.of(player), "item"));
    }
}
