package com.torturemod.feature.help;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetId;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Картинки справки на ванильных картах. PNG лежат в config/torturemod/help: при первом запуске туда
 * копируются встроенные. Картинка режется на квадраты 128×128, каждый становится картой для рамки.
 */
final class HelpMaps {
    static final List<String> BUILT_IN = List.of("01_prison_marking.png", "02_console_and_chests.png",
            "03_cuffs.png", "04_escape.png", "05_quota.png", "06_ransom.png", "07_minions.png");
    private static final int TILE = 128;

    private final FeatureContext context;

    HelpMaps(FeatureContext context) {
        this.context = context;
    }

    record Tile(String name, int[] argb) {
    }

    void give(TargetId player, Consumer<String> reply) {
        Path folder = context.platform().configDirectory().resolve("help");
        // Чтение и нарезка картинок идут в фоне, выдача карт на основном потоке.
        context.platform().scheduler().supplyAsync(() -> load(folder), tiles -> {
            for (Tile tile : tiles) {
                context.platform().items().giveMap(player, tile.name(), tile.argb());
            }
            reply.accept(context.messages().text("help.maps_given", tiles.size(), folder.toString()));
        }, error -> {
            context.platform().log().error("Cannot load help pictures", error);
            reply.accept(context.messages().text("help.maps_failed"));
        });
    }

    static List<Tile> load(Path folder) {
        try {
            installDefaults(folder);
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.png")) {
                stream.forEach(files::add);
            }
            files.sort(null);
            List<Tile> tiles = new ArrayList<>();
            for (Path file : files) {
                BufferedImage image = ImageIO.read(file.toFile());
                if (image != null) {
                    tiles.addAll(cut(file.getFileName().toString().replace(".png", ""), image));
                }
            }
            return tiles;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void installDefaults(Path folder) throws IOException {
        if (Files.isDirectory(folder)) {
            return;
        }
        Files.createDirectories(folder);
        for (String name : BUILT_IN) {
            try (InputStream in = HelpMaps.class.getResourceAsStream("/torturemod/help/" + name)) {
                if (in != null) {
                    Files.copy(in, folder.resolve(name));
                }
            }
        }
    }

    /** Картинка подгоняется под целое число карт по ширине и высоте и режется на квадраты. */
    static List<Tile> cut(String name, BufferedImage image) {
        int columns = Math.max(1, Math.round(image.getWidth() / (float) TILE));
        int rows = Math.max(1, Math.round(image.getHeight() / (float) TILE));
        BufferedImage scaled = new BufferedImage(columns * TILE, rows * TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, columns * TILE, rows * TILE, null);
        graphics.dispose();
        List<Tile> tiles = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int[] argb = scaled.getRGB(column * TILE, row * TILE, TILE, TILE, null, 0, TILE);
                String label = columns * rows == 1 ? name : name + " " + (row + 1) + "-" + (column + 1);
                tiles.add(new Tile(label, argb));
            }
        }
        return tiles;
    }
}
