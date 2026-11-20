package com.torturemod.feature.items;

import com.torturemod.api.feature.ConfigReloaded;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.items.Classification;
import com.torturemod.api.items.ItemClassifier;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Permissions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Автоопределение типа пытки и правила конфига. Предоставляет {@link ItemClassifier}. */
public final class ItemsFeature implements Feature {
    private FeatureContext context;
    private RuleClassifier classifier;
    private EventBus.Subscription reloadSubscription;

    @Override
    public String id() {
        return "items";
    }

    @Override
    public void start(FeatureContext context) {
        this.context = context;
        classifier = new RuleClassifier(context);
        context.services().provide(ItemClassifier.class, classifier);
        reloadSubscription = context.bus().subscribe(ConfigReloaded.class, event -> classifier.reload());
        context.commands().register("item", Permissions.USE, this::describeHeld);
        context.commands().register("items-report", Permissions.ADMIN, this::writeReport);
    }

    @Override
    public void stop() {
        if (reloadSubscription != null) {
            reloadSubscription.cancel();
        }
    }

    private void describeHeld(Commands.Invocation invocation) {
        Optional<String> held = invocation.sender().flatMap(sender -> context.platform().items().heldItem(sender));
        if (held.isEmpty()) {
            invocation.reply().accept(context.messages().text("items.empty_hand"));
            return;
        }
        invocation.reply().accept(describe(classifier.classify(held.get())));
    }

    private void writeReport(Commands.Invocation invocation) {
        List<String> lines = new ArrayList<>();
        lines.add("# item\ttypes\tstrength\treason");
        for (String itemId : context.platform().items().allItemIds()) {
            Classification result = classifier.classify(itemId);
            lines.add(String.join("\t", itemId, String.join(",", result.types()),
                    format(result.strength()), result.reason()));
        }
        // Классификация читает реестр игры и идёт здесь, запись файла уходит в фон.
        Path file = context.platform().configDirectory().resolve("items-report.txt");
        context.platform().scheduler().supplyAsync(() -> {
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, lines, StandardCharsets.UTF_8);
                return file;
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + file, e);
            }
        }, written -> invocation.reply().accept(context.messages().text("items.report_written", written, lines.size() - 1)),
                error -> {
                    context.platform().log().error("Items report failed", error);
                    invocation.reply().accept(context.messages().text("items.report_failed"));
                });
    }

    private String describe(Classification result) {
        if (result.types().isEmpty()) {
            return context.messages().text("items.not_used", result.itemId(), result.reason());
        }
        return context.messages().text("items.classified", result.itemId(), String.join(", ", result.types()),
                format(result.strength()), result.reason());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
