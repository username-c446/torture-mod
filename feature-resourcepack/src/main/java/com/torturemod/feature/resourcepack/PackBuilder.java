package com.torturemod.feature.resourcepack;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.resources.ResourcePackContents;
import com.torturemod.api.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Собирает zip ресурспака в фоне: одинаковое содержимое даёт одинаковый файл и SHA-1. */
final class PackBuilder implements ResourcePackContents {
    /** Формат ресурспака для Minecraft 1.20.1. */
    static final int PACK_FORMAT = 15;
    static final String FILE_NAME = "resourcepack.zip";

    private final FeatureContext context;
    private final Map<String, byte[]> files = new TreeMap<>();
    /** Ванильный предмет → номер модели → модель. */
    private final Map<String, Map<Integer, String>> overrides = new TreeMap<>();
    private volatile BuiltPack built;

    PackBuilder(FeatureContext context) {
        this.context = context;
    }

    @Override
    public void add(String path, byte[] content) {
        files.put(path, content.clone());
    }

    @Override
    public void overrideItemModel(String baseItemId, int customModelData, String model) {
        overrides.computeIfAbsent(baseItemId, key -> new TreeMap<>()).put(customModelData, model);
    }

    @Override
    public Optional<BuiltPack> built() {
        return Optional.ofNullable(built);
    }

    /** Снимок файлов берётся на основном потоке, zip пишется в фоне. */
    void build() {
        Map<String, byte[]> snapshot = new LinkedHashMap<>(contents());
        Path folder = context.platform().configDirectory();
        List<String> copies = context.config().has("resource_pack.copy_to")
                ? context.config().getStringList("resource_pack.copy_to") : List.of();
        context.platform().scheduler().supplyAsync(() -> write(snapshot, folder, copies), pack -> {
            built = pack;
            context.platform().log().info("Resource pack: " + pack.file() + ", SHA-1 " + pack.sha1());
        }, error -> context.platform().log().error("Cannot build the resource pack", error));
    }

    Map<String, byte[]> contents() {
        Map<String, byte[]> all = new TreeMap<>(files);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("pack", Map.of("pack_format", PACK_FORMAT, "description", "Torture Mod"));
        all.put("pack.mcmeta", Json.write(meta).getBytes(StandardCharsets.UTF_8));
        overrides.forEach((baseItem, models) -> {
            String name = baseItem.substring(baseItem.indexOf(':') + 1);
            List<Object> list = new ArrayList<>();
            models.forEach((data, model) -> list.add(Map.of("predicate", Map.of("custom_model_data", data), "model", model)));
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("parent", "minecraft:item/generated");
            json.put("textures", Map.of("layer0", "minecraft:item/" + name));
            json.put("overrides", list);
            all.put("assets/minecraft/models/item/" + name + ".json", Json.write(json).getBytes(StandardCharsets.UTF_8));
        });
        return all;
    }

    static BuiltPack write(Map<String, byte[]> files, Path folder, List<String> copies) {
        try {
            byte[] zip = zip(files);
            Files.createDirectories(folder);
            Path file = folder.resolve(FILE_NAME);
            Files.write(file, zip);
            for (String copy : copies) {
                Path target = Path.of(copy);
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new BuiltPack(file, sha1(zip));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Время записей фиксировано: иначе SHA-1 менялся бы при каждом запуске и клиенты качали бы пак заново. */
    static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(files).entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0);
                out.putNextEntry(zipEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static String sha1(byte[] data) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1").digest(data)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
