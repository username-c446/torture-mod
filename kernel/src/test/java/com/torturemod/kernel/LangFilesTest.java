package com.torturemod.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.Map;
import java.util.TreeSet;

/** Тексты на двух языках: файлы читаются, и в них одинаковые ключи. */
class LangFilesTest {
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/torturemod/" + resource)) {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        }
    }

    @Test
    void russianAndEnglishHaveTheSameKeys() throws Exception {
        Map<String, Object> ru = load("lang/ru.yml");
        Map<String, Object> en = load("lang/en.yml");
        TreeSet<String> onlyRu = new TreeSet<>(ru.keySet());
        onlyRu.removeAll(en.keySet());
        TreeSet<String> onlyEn = new TreeSet<>(en.keySet());
        onlyEn.removeAll(ru.keySet());
        assertEquals(new TreeSet<>(), onlyRu, "missing in en.yml");
        assertEquals(new TreeSet<>(), onlyEn, "missing in ru.yml");
    }

    @Test
    void configParses() throws Exception {
        Map<String, Object> config = load("config.yml");
        assertTrue(config.containsKey("prison"));
        assertTrue(config.containsKey("minions"));
        assertTrue(config.containsKey("cuffs"));
    }
}
