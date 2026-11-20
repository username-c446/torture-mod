package com.torturemod.feature.help;

import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.help.Hints;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Permissions;

/**
 * Справка для игроков без мода на клиенте: книга с главами, картинки на картах и подсказки по месту.
 * Запускается первой: подсказки нужны остальным фичам.
 */
public final class HelpFeature implements Feature {
    private HintBook hints;

    @Override
    public String id() {
        return "help";
    }

    @Override
    public void start(FeatureContext context) {
        hints = new HintBook(context);
        hints.load();
        context.services().provide(Hints.class, hints);
        GuideBook book = new GuideBook(context);
        HelpMaps maps = new HelpMaps(context);
        context.commands().register("help", Permissions.USE, invocation -> {
            if (!invocation.args().isEmpty() && invocation.args().get(0).equals("maps")) {
                giveMaps(context, maps, invocation);
                return;
            }
            if (invocation.sender().isEmpty()) {
                invocation.reply().accept(context.messages().text("help.console"));
                return;
            }
            context.platform().feedback().openBook(invocation.sender().get(), book.build());
        });
    }

    private static void giveMaps(FeatureContext context, HelpMaps maps, Commands.Invocation invocation) {
        if (invocation.sender().isEmpty()) {
            invocation.reply().accept(context.messages().text("help.console"));
            return;
        }
        if (!context.platform().permissions().has(invocation.sender().get(), Permissions.ADMIN)) {
            invocation.reply().accept(context.messages().text("command.no_permission"));
            return;
        }
        maps.give(invocation.sender().get(), invocation.reply());
    }
}
