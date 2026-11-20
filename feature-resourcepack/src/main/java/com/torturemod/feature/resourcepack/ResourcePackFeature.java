package com.torturemod.feature.resourcepack;

import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.resources.ResourcePackContents;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ресурспак сервера: текстуры и модели предметов мода. Мод собирает zip в config/torturemod,
 * копирует его туда, откуда его раздают (папка плагина ForcePack, веб-сервер), и, если задана ссылка,
 * сам предлагает пак игрокам при входе.
 */
public final class ResourcePackFeature implements Feature {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    @Override
    public String id() {
        return "resourcepack";
    }

    @Override
    public void start(FeatureContext context) {
        PackBuilder pack = new PackBuilder(context);
        context.services().provide(ResourcePackContents.class, pack);
        // Остальные фичи кладут файлы при своём запуске, поэтому пак собирается на первом тике.
        context.platform().scheduler().later(1, pack::build);
        subscriptions.add(context.bus().subscribe(GameEvent.PlayerJoined.class, event -> {
            String url = context.config().getString("resource_pack.url", "");
            if (url.isBlank() || pack.built().isEmpty()) {
                return;
            }
            context.platform().resourcePacks().offer(event.player(), url, pack.built().get().sha1(),
                    context.config().getBoolean("resource_pack.required", false),
                    context.messages().text("resourcepack.prompt"));
        }));
        context.commands().register("resourcepack", Permissions.ADMIN, invocation -> {
            if (pack.built().isEmpty()) {
                invocation.reply().accept(context.messages().text("resourcepack.building"));
                return;
            }
            Path file = pack.built().get().file();
            String url = context.config().getString("resource_pack.url", "");
            invocation.reply().accept(context.messages().text("resourcepack.info", file, pack.built().get().sha1(),
                    url.isBlank() ? context.messages().text("resourcepack.no_url") : url));
        });
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
    }
}
