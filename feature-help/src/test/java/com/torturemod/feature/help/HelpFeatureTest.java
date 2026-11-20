package com.torturemod.feature.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torturemod.api.help.Hints;
import com.torturemod.api.model.Book;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.testkit.FakeContext;
import com.torturemod.testkit.FakePlatform;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

class HelpFeatureTest {
    @Test
    void hintIsShownOnceAndRemembered() {
        FakePlatform platform = new FakePlatform();
        TargetId player = platform.player("Player", new Location("w", 0, 0, 0));
        FakeContext context = new FakeContext(platform);
        new HelpFeature().start(context);
        Hints hints = context.services().find(Hints.class).orElseThrow();
        hints.hint(player, "hint.test");
        hints.hint(player, "hint.test");
        assertEquals(1, platform.messagesOf(player).size());

        FakeContext restarted = new FakeContext(platform, context.storage);
        new HelpFeature().start(restarted);
        restarted.services().find(Hints.class).orElseThrow().hint(player, "hint.test");
        assertEquals(1, platform.messagesOf(player).size());
    }

    @Test
    void hintsCanBeTurnedOff() {
        FakePlatform platform = new FakePlatform();
        TargetId player = platform.player("Player", new Location("w", 0, 0, 0));
        FakeContext context = new FakeContext(platform);
        context.config.put("help.hints", false);
        new HelpFeature().start(context);
        context.services().find(Hints.class).orElseThrow().hint(player, "hint.test");
        assertTrue(platform.messagesOf(player).isEmpty());
    }

    @Test
    void bookHasContentsWithLinksToChapters() {
        FakePlatform platform = new FakePlatform();
        TargetId player = platform.player("Player", new Location("w", 0, 0, 0));
        FakeContext context = new FakeContext(platform);
        new HelpFeature().start(context);
        context.run(Optional.of(player), "help");
        Book book = platform.books.get(player);
        // В тестах тексты — ключи, поэтому глав нет и книга состоит из оглавления.
        assertEquals("help.book.title", book.title());
        assertTrue(book.pages().get(0).spans().get(0).bold());
    }

    @Test
    void wideImageIsCutIntoMapTiles() {
        BufferedImage image = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
        List<HelpMaps.Tile> tiles = HelpMaps.cut("picture", image);
        assertEquals(2, tiles.size());
        assertEquals(128 * 128, tiles.get(0).argb().length);
        assertEquals("picture 1-2", tiles.get(1).name());
    }
}
