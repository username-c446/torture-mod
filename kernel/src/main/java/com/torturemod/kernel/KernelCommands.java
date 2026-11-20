package com.torturemod.kernel;

import com.torturemod.api.Platform;
import com.torturemod.api.feature.CommandRegistry;
import com.torturemod.api.feature.Messages;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Permissions;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Разбирает /torturemod и передаёт аргументы подкоманде фичи. */
final class KernelCommands implements CommandRegistry {
    private record Entry(String permission, Commands.Handler handler) {
    }

    private final Platform platform;
    private final Supplier<Messages> messages;
    private final Map<String, Entry> entries = new TreeMap<>();

    KernelCommands(Platform platform, Supplier<Messages> messages) {
        this.platform = platform;
        this.messages = messages;
    }

    @Override
    public void register(String name, String permission, Commands.Handler handler) {
        if (entries.putIfAbsent(name, new Entry(permission, handler)) != null) {
            throw new IllegalStateException("Subcommand already registered: " + name);
        }
    }

    void handle(Commands.Invocation invocation) {
        List<String> args = invocation.args();
        Entry entry = args.isEmpty() ? null : entries.get(args.get(0));
        if (entry == null) {
            invocation.reply().accept(messages.get().text("command.help", String.join(", ", entries.keySet())));
            return;
        }
        boolean allowed = invocation.sender()
                .map(sender -> platform.permissions().has(sender, entry.permission()))
                .orElse(true);
        if (!allowed) {
            invocation.reply().accept(messages.get().text("command.no_permission"));
            return;
        }
        entry.handler().handle(new Commands.Invocation(
                invocation.sender(), List.copyOf(args.subList(1, args.size())), invocation.reply()));
    }

    void registerReload(java.util.function.Consumer<java.util.function.Consumer<Boolean>> reload) {
        register("reload", Permissions.ADMIN, invocation -> reload.accept(ok -> invocation.reply().accept(
                messages.get().text(ok ? "command.reloaded" : "command.reload_failed"))));
    }

    void registerPerf(Supplier<String> report) {
        register("perf", Permissions.ADMIN, invocation -> invocation.reply().accept(report.get()));
    }
}
