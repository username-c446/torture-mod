package com.torturemod.kernel;

import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.feature.MapConfigView;

import java.nio.file.Path;

final class YamlConfig {
    private YamlConfig() {
    }

    static ConfigView load(Path configDirectory) {
        return new MapConfigView(YamlFiles.loadWithDefaults(configDirectory.resolve("config.yml"), "torturemod/config.yml"));
    }
}
