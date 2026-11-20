package com.torturemod.kernel;

import com.torturemod.api.feature.Messages;

import java.nio.file.Path;
import java.util.Map;

final class YamlMessages implements Messages {
    private final Map<String, Object> texts;

    private YamlMessages(Map<String, Object> texts) {
        this.texts = texts;
    }

    static YamlMessages load(Path configDirectory, String language) {
        // Язык попадает в путь к файлу, поэтому принимаем только известные значения.
        String safe = "en".equals(language) ? "en" : "ru";
        return new YamlMessages(YamlFiles.loadWithDefaults(
                configDirectory.resolve("lang").resolve(safe + ".yml"), "torturemod/lang/" + safe + ".yml"));
    }

    @Override
    public String text(String key, Object... args) {
        Object value = texts.get(key);
        if (value == null) {
            return key;
        }
        String text = value.toString();
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }
}
