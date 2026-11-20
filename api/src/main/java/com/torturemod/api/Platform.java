package com.torturemod.api;

import com.torturemod.api.port.ResourcePacks;
import com.torturemod.api.port.BlockQueries;
import com.torturemod.api.port.Bodies;
import com.torturemod.api.port.Combat;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Containers;
import com.torturemod.api.port.Convoy;
import com.torturemod.api.port.Feedback;
import com.torturemod.api.port.GameEvents;
import com.torturemod.api.port.Inventories;
import com.torturemod.api.port.Items;
import com.torturemod.api.port.Log;
import com.torturemod.api.port.Minions;
import com.torturemod.api.port.Menus;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.port.Players;
import com.torturemod.api.port.RegionPolicy;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.port.Targets;

import java.nio.file.Path;

/** Всё, что нужно ядру от платформы. Реализуется прослойкой Forge, позже прослойкой Paper. */
public interface Platform {
    Log log();

    Scheduler scheduler();

    Targets targets();

    Players players();

    Combat combat();

    BlockQueries blocks();

    Items items();

    Menus menus();

    Feedback feedback();

    Inventories inventories();

    Bodies bodies();

    Convoy convoy();

    Containers containers();

    Minions minions();

    ResourcePacks resourcePacks();

    Permissions permissions();

    RegionPolicy regionPolicy();

    Commands commands();

    GameEvents gameEvents();

    /** Конфиги и тексты. */
    Path configDirectory();

    /** Данные: база плена и журнал. */
    Path dataDirectory();
}
