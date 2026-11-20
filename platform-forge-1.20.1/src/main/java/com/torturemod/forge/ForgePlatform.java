package com.torturemod.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.torturemod.api.port.ResourcePacks;
import com.torturemod.api.port.Minions;
import com.torturemod.api.port.Containers;
import com.torturemod.api.Platform;
import com.torturemod.api.port.BlockQueries;
import com.torturemod.api.port.Bodies;
import com.torturemod.api.port.Combat;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Convoy;
import com.torturemod.api.port.Feedback;
import com.torturemod.api.port.GameEvents;
import com.torturemod.api.port.Inventories;
import com.torturemod.api.port.Items;
import com.torturemod.api.port.Log;
import com.torturemod.api.port.Menus;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.port.Players;
import com.torturemod.api.port.RegionPolicy;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.port.Targets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

final class ForgePlatform implements Platform {
    private final MinecraftServer server;
    private final Log log = new ForgeLog();
    private final ForgeScheduler scheduler;
    private final ForgeBodies bodies;
    private final Targets targets;
    private final Players players;
    private final BlockQueries blocks;
    private final Items items;
    private final Combat combat;
    private final Menus menus;
    private final Inventories inventories;
    private final Feedback feedback;
    private final Permissions permissions;
    private final ForgeCommands commands;
    private final ForgeGameEvents gameEvents = new ForgeGameEvents();
    private final ForgeConvoy convoy;
    private final Containers containers;
    private final Minions minions;

    ForgePlatform(MinecraftServer server) {
        this.server = server;
        this.scheduler = new ForgeScheduler(server);
        this.bodies = new ForgeBodies(server);
        ForgeEntities entities = new ForgeEntities(server, bodies);
        this.targets = new ForgeTargets(server, entities);
        this.players = new ForgePlayers(server, entities);
        this.blocks = new ForgeBlocks(server);
        this.items = new ForgeItems(server);
        this.combat = new ForgeCombat(server, entities);
        this.menus = new ForgeMenus(server);
        this.inventories = new ForgeInventories(entities);
        this.convoy = new ForgeConvoy(entities);
        this.containers = new ForgeContainers(server);
        this.minions = new ForgeMinions(entities);
        this.feedback = new ForgeFeedback(server);
        this.permissions = new ForgePermissions(server);
        this.commands = new ForgeCommands(server);
    }

    void onTick() {
        scheduler.tick();
        bodies.tick();
        convoy.tick();
    }

    void shutdown() {
        convoy.removeAll();
        scheduler.shutdown();
    }

    ForgeBodies bodiesImpl() {
        return bodies;
    }

    ForgeCommands commandsImpl() {
        return commands;
    }

    ForgeGameEvents gameEventsImpl() {
        return gameEvents;
    }

    @Override
    public Log log() {
        return log;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public Targets targets() {
        return targets;
    }

    @Override
    public Players players() {
        return players;
    }

    @Override
    public Combat combat() {
        return combat;
    }

    @Override
    public BlockQueries blocks() {
        return blocks;
    }

    @Override
    public Items items() {
        return items;
    }

    @Override
    public Menus menus() {
        return menus;
    }

    @Override
    public Feedback feedback() {
        return feedback;
    }

    @Override
    public Inventories inventories() {
        return inventories;
    }

    @Override
    public Bodies bodies() {
        return bodies;
    }

    @Override
    public Convoy convoy() {
        return convoy;
    }

    @Override
    public Containers containers() {
        return containers;
    }

    @Override
    public Minions minions() {
        return minions;
    }

    @Override
    public ResourcePacks resourcePacks() {
        return (player, url, sha1, required, prompt) -> {
            ServerPlayer online = server.getPlayerList().getPlayer(player.value());
            if (online != null) {
                online.sendTexturePack(url, sha1, required, Component.literal(prompt));
            }
        };
    }

    @Override
    public Permissions permissions() {
        return permissions;
    }

    @Override
    public RegionPolicy regionPolicy() {
        return RegionPolicy.ALLOW_ALL;
    }

    @Override
    public Commands commands() {
        return commands;
    }

    @Override
    public GameEvents gameEvents() {
        return gameEvents;
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("torturemod");
    }

    @Override
    public Path dataDirectory() {
        return server.getWorldPath(LevelResource.ROOT).resolve("torturemod").normalize();
    }
}
