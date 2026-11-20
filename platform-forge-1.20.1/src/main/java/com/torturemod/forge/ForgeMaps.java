package com.torturemod.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Картинки на ванильных картах: каждый пиксель берёт ближайший цвет палитры карт. */
final class ForgeMaps {
    private static final int[] PALETTE_RGB = new int[256];
    private static final byte[] PALETTE_ID = new byte[256];
    private static int paletteSize;

    static {
        for (int id = 1; id < 64; id++) {
            MapColor color = MapColor.byId(id);
            if (color == null || color == MapColor.NONE) {
                continue;
            }
            for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
                int abgr = color.calculateRGBColor(brightness);
                int r = abgr & 255, g = abgr >> 8 & 255, b = abgr >> 16 & 255;
                PALETTE_RGB[paletteSize] = r << 16 | g << 8 | b;
                PALETTE_ID[paletteSize] = color.getPackedId(brightness);
                paletteSize++;
            }
        }
    }

    private ForgeMaps() {
    }

    static void give(MinecraftServer server, ServerPlayer player, String name, int[] argb) {
        ServerLevel level = server.overworld();
        int id = level.getFreeMapId();
        MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD).locked();
        for (int i = 0; i < 128 * 128 && i < argb.length; i++) {
            data.colors[i] = color(argb[i]);
        }
        data.setDirty();
        level.setMapData(MapItem.makeKey(id), data);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.getOrCreateTag().putInt("map", id);
        map.setHoverName(Component.literal(name));
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
    }

    private static byte color(int argb) {
        if ((argb >>> 24) < 128) {
            return 0;
        }
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < paletteSize; i++) {
            int c = PALETTE_RGB[i];
            long dr = r - (c >> 16 & 255), dg = g - (c >> 8 & 255), db = b - (c & 255);
            // Глаз чувствительнее к зелёному: веса как у яркости.
            long distance = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return PALETTE_ID[best];
    }
}
