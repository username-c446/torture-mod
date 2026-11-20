package com.torturemod.forge;

import com.mojang.logging.LogUtils;
import com.torturemod.bundle.Bundle;
import com.torturemod.kernel.Kernel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Точка входа Forge: жизненный цикл ядра. События игры переводит {@link ForgeEventBridge}. */
@Mod("torturemod")
public final class TortureMod {
    private volatile ForgePlatform platform;
    private Kernel kernel;

    public TortureMod() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ForgeEventBridge(() -> platform));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ForgePlatform created = new ForgePlatform(event.getServer());
        Kernel createdKernel = Bundle.createKernel(created);
        try {
            platform = created;
            createdKernel.start();
            kernel = createdKernel;
        } catch (RuntimeException e) {
            // Сбой мода не должен останавливать сервер: без плена игра продолжается как обычно.
            LogUtils.getLogger().error("Torture Mod failed to start and is disabled", e);
            platform = null;
            createdKernel.stop();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (kernel != null) {
            kernel.stop();
            kernel = null;
        }
        if (platform != null) {
            // Тела сохраняются в файлы игроков, пока мир ещё на месте.
            platform.bodiesImpl().removeAll();
            platform.shutdown();
        }
        platform = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        ForgePlatform current = platform;
        if (current != null && event.phase == TickEvent.Phase.END) {
            current.onTick();
        }
    }

    /** Срабатывает до запуска сервера и при каждой команде /reload. */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ForgePlatform current = platform;
        if (current != null) {
            current.commandsImpl().attachAll(event.getDispatcher());
        }
    }
}
