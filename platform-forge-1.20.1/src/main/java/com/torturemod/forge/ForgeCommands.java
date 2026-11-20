package com.torturemod.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ядро запускается после RegisterCommandsEvent, поэтому команда добавляется в работающий диспетчер,
 * а список хранится для повторной регистрации при /reload.
 */
final class ForgeCommands implements Commands {
    private static final String ARGS = "args";

    private final MinecraftServer server;
    private final Map<String, Handler> handlers = new LinkedHashMap<>();

    ForgeCommands(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void register(String root, Handler handler) {
        handlers.put(root, handler);
        attach(server.getCommands().getDispatcher(), root, handler);
    }

    void attachAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        handlers.forEach((root, handler) -> attach(dispatcher, root, handler));
    }

    private static void attach(CommandDispatcher<CommandSourceStack> dispatcher, String root, Handler handler) {
        dispatcher.register(net.minecraft.commands.Commands.literal(root)
                .executes(context -> run(context, handler, ""))
                .then(net.minecraft.commands.Commands.argument(ARGS, StringArgumentType.greedyString())
                        .executes(context -> run(context, handler, StringArgumentType.getString(context, ARGS)))));
    }

    private static int run(CommandContext<CommandSourceStack> context, Handler handler, String args) {
        CommandSourceStack source = context.getSource();
        Optional<TargetId> sender = source.getEntity() instanceof ServerPlayer player
                ? Optional.of(new TargetId(player.getUUID()))
                : Optional.empty();
        List<String> parts = args.isBlank() ? List.of() : List.of(args.trim().split("\\s+"));
        handler.handle(new Invocation(sender, parts, text -> source.sendSystemMessage(Component.literal(text))));
        return 1;
    }
}
