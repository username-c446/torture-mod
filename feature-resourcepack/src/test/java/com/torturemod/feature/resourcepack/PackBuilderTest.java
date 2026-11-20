package com.torturemod.feature.resourcepack;

import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.resources.ResourcePackContents;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

class PackBuilderTest {
    @Test
    void itemModelOverridesAreMergedPerBaseItem() {
        PackBuilder pack = new PackBuilder(new FakeContext(new FakePlatform()));
        pack.overrideItemModel("minecraft:chain", 2, "torturemod:item/b");
        pack.overrideItemModel("minecraft:chain", 1, "torturemod:item/a");
        String model = new String(pack.contents().get("assets/minecraft/models/item/chain.json"), StandardCharsets.UTF_8);
        assertTrue(model.indexOf("torturemod:item/a") < model.indexOf("torturemod:item/b"));
        assertTrue(model.contains("\"layer0\":\"minecraft:item/chain\""));
        assertTrue(pack.contents().containsKey("pack.mcmeta"));
    }

    @Test
    void sameContentGivesSameZip() throws Exception {
        Map<String, byte[]> files = Map.of("a.txt", new byte[]{1, 2}, "b/c.txt", new byte[]{3});
        assertArrayEquals(PackBuilder.zip(files), PackBuilder.zip(files));
        assertEquals(40, PackBuilder.sha1(PackBuilder.zip(files)).length());
    }

    @Test
    void packIsOfferedOnJoinWhenUrlIsSet(@TempDir Path folder) {
        FakePlatform platform = new FakePlatform();
        platform.configDirectory = folder;
        TargetId player = platform.player("Player", new Location("w", 0, 0, 0));
        FakeContext context = new FakeContext(platform);
        context.config.put("resource_pack.url", "https://example.org/pack.zip");
        new ResourcePackFeature().start(context);
        platform.tick(2);
        assertTrue(java.nio.file.Files.exists(folder.resolve("resourcepack.zip")));
        assertTrue(context.services().find(ResourcePackContents.class).orElseThrow().built().isPresent());
        platform.fire(new GameEvent.PlayerJoined(player));
        assertEquals(1, platform.packOffers.size());
        assertTrue(platform.packOffers.get(0).startsWith("https://example.org/pack.zip|"));
    }
}
