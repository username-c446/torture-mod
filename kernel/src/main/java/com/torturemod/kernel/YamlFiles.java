package com.torturemod.kernel;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Читает YAML поверх встроенных значений: новые ключи работают и со старым файлом пользователя. */
final class YamlFiles {
    private YamlFiles() {
    }

    static Map<String, Object> loadWithDefaults(Path file, String resource) {
        Map<String, Object> defaults = readResource(resource);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream in = openResource(resource)) {
                    Files.copy(in, file);
                }
                return defaults;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return merge(defaults, parse(reader));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static Map<String, Object> readResource(String resource) {
        try (InputStream in = openResource(resource)) {
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read resource " + resource, e);
        }
    }

    private static InputStream openResource(String resource) throws IOException {
        InputStream in = YamlFiles.class.getResourceAsStream("/" + resource);
        if (in == null) {
            throw new IOException("Missing resource " + resource);
        }
        return in;
    }

    private static Map<String, Object> parse(Reader reader) {
        Map<String, Object> parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        return parsed == null ? Map.of() : parsed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        override.forEach((key, value) -> {
            if (value instanceof Map<?, ?> overrideMap && result.get(key) instanceof Map<?, ?> baseMap) {
                result.put(key, merge((Map<String, Object>) baseMap, (Map<String, Object>) overrideMap));
            } else {
                result.put(key, value);
            }
        });
        return result;
    }
}
