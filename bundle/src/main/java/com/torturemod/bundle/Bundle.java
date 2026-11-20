package com.torturemod.bundle;

import com.torturemod.feature.resourcepack.ResourcePackFeature;
import com.torturemod.feature.minions.MinionsFeature;
import com.torturemod.feature.prison.PrisonFeature;
import com.torturemod.feature.cuffs.CuffsFeature;
import com.torturemod.feature.help.HelpFeature;
import com.torturemod.api.Platform;
import com.torturemod.api.feature.Feature;
import com.torturemod.feature.admin.AdminFeature;
import com.torturemod.feature.captivity.CaptivityFeature;
import com.torturemod.feature.convoy.ConvoyFeature;
import com.torturemod.feature.escape.EscapeFeature;
import com.torturemod.feature.interrogation.InterrogationFeature;
import com.torturemod.feature.items.ItemsFeature;
import com.torturemod.feature.offline.OfflineFeature;
import com.torturemod.feature.resistance.ResistanceFeature;
import com.torturemod.feature.torture.TortureFeature;
import com.torturemod.kernel.Kernel;
import com.torturemod.storage.sqlite.SqliteStorage;

import java.util.List;

/** Состав мода. Новая фича подключается здесь, код платформ при этом не меняется. */
public final class Bundle {
    private Bundle() {
    }

    public static Kernel createKernel(Platform platform) {
        // Порядок важен: фича находит сервисы тех, что запущены раньше неё.
        List<Feature> features = List.of(
                new HelpFeature(),
                new ResourcePackFeature(),
                new CaptivityFeature(),
                new ItemsFeature(),
                new ResistanceFeature(),
                new TortureFeature(),
                new OfflineFeature(),
                new CuffsFeature(),
                new PrisonFeature(),
                new EscapeFeature(),
                new InterrogationFeature(),
                new ConvoyFeature(),
                new MinionsFeature(),
                new AdminFeature());
        return new Kernel(platform, new SqliteStorage(platform.dataDirectory().resolve("torturemod.db")), features);
    }
}
