package com.torturemod.feature.captivity;

import com.torturemod.api.captivity.CaptureFailure;
import com.torturemod.api.captivity.ReleaseReason;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.CaptivityRecord;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Permissions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** /torturemod capture, release, list. */
final class CaptivityCommands {
    private final FeatureContext context;
    private final CaptivityRegistry registry;

    CaptivityCommands(FeatureContext context, CaptivityRegistry registry) {
        this.context = context;
        this.registry = registry;
    }

    void register() {
        context.commands().register("capture", Permissions.ADMIN, this::capture);
        context.commands().register("release", Permissions.ADMIN, this::release);
        context.commands().register("list", Permissions.ADMIN, this::list);
    }

    private void capture(Commands.Invocation invocation) {
        Optional<TargetId> target = singleTarget(invocation, "command.capture.usage");
        if (target.isEmpty()) {
            return;
        }
        Optional<CaptureFailure> failure = registry.captureWithoutCell(target.get(), invocation.sender());
        invocation.reply().accept(failure
                .map(reason -> text("captivity.failure." + reason.name()))
                .orElseGet(() -> text("command.captured", registry.displayName(target.get()))));
    }

    private void release(Commands.Invocation invocation) {
        Optional<TargetId> target = singleTarget(invocation, "command.release.usage");
        if (target.isEmpty()) {
            return;
        }
        String name = registry.displayName(target.get());
        invocation.reply().accept(registry.release(target.get(), ReleaseReason.ADMIN)
                ? text("command.released", name)
                : text("command.not_captive", name));
    }

    private void list(Commands.Invocation invocation) {
        List<CaptivityRecord> records = registry.records();
        if (records.isEmpty()) {
            invocation.reply().accept(text("command.list.empty"));
            return;
        }
        invocation.reply().accept(text("command.list.header", records.size()));
        for (CaptivityRecord record : records) {
            String cell = record.cell().map(CaptivityCommands::format).orElseGet(() -> text("command.list.no_cell"));
            invocation.reply().accept(text("command.list.entry",
                    registry.displayName(record.target()), record.kind().name().toLowerCase(), cell,
                    Math.round(record.resistance())));
        }
    }

    private Optional<TargetId> singleTarget(Commands.Invocation invocation, String usageKey) {
        if (invocation.args().size() != 1) {
            invocation.reply().accept(text(usageKey));
            return Optional.empty();
        }
        String argument = invocation.args().get(0);
        Optional<TargetId> target = context.platform().targets().resolvePlayer(argument).or(() -> parseUuid(argument));
        if (target.isEmpty()) {
            invocation.reply().accept(text("command.target_not_found", argument));
        }
        return target;
    }

    private static Optional<TargetId> parseUuid(String value) {
        try {
            return Optional.of(new TargetId(UUID.fromString(value)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String format(BlockLocation block) {
        return block.worldId() + " " + block.x() + " " + block.y() + " " + block.z();
    }

    private String text(String key, Object... args) {
        return context.messages().text(key, args);
    }
}
