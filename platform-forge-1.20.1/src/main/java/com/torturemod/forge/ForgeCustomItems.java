package com.torturemod.forge;

import com.torturemod.api.items.CustomItem;
import com.torturemod.api.items.ShapedRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Предметы мода без регистрации новых предметов: ванильная стопка с меткой {@value #TAG} и номером
 * модели для ресурспака. Рецепты добавляются в менеджер рецептов сервера и уходят клиенту как обычные.
 */
final class ForgeCustomItems {
    static final String TAG = "torturemod_item";

    private static final Map<String, CustomItem> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, ShapedRecipe> RECIPES = new ConcurrentHashMap<>();

    private ForgeCustomItems() {
    }

    static void define(CustomItem item) {
        ITEMS.put(item.id(), item);
    }

    static Optional<CustomItem> get(String id) {
        return Optional.ofNullable(ITEMS.get(id));
    }

    /** ID предмета мода в стопке; пусто для обычной стопки. */
    static Optional<String> idOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG)) {
            return Optional.empty();
        }
        String id = tag.getString(TAG);
        return ITEMS.containsKey(id) ? Optional.of(id) : Optional.empty();
    }

    static Item baseItem(CustomItem item) {
        ResourceLocation id = ResourceLocation.tryParse(item.baseItemId());
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
    }

    static ItemStack create(CustomItem item, int count) {
        ItemStack stack = new ItemStack(baseItem(item), count);
        if (stack.isEmpty()) {
            return stack;
        }
        stack.getOrCreateTag().putString(TAG, item.id());
        stack.getOrCreateTag().putInt("CustomModelData", item.customModelData());
        stack.setHoverName(Component.literal(item.name()).withStyle(style -> style.withItalic(false)));
        if (!item.lore().isEmpty()) {
            ListTag lore = new ListTag();
            for (String line : item.lore()) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line)
                        .withStyle(style -> style.withItalic(false).withColor(0xAAAAAA)))));
            }
            stack.getOrCreateTagElement("display").put("Lore", lore);
        }
        return stack;
    }

    static void addRecipe(MinecraftServer server, ShapedRecipe recipe) {
        RECIPES.put(recipe.id(), recipe);
        inject(server, server.getPlayerList().getPlayers());
    }

    /**
     * Добавляет рецепты мода в менеджер, если их там нет (после /reload менеджер пересобирается),
     * и отправляет игрокам обновлённый список.
     */
    static void inject(MinecraftServer server, Collection<ServerPlayer> players) {
        if (RECIPES.isEmpty()) {
            return;
        }
        RecipeManager manager = server.getRecipeManager();
        boolean missing = RECIPES.values().stream().anyMatch(recipe -> manager.byKey(location(recipe)).isEmpty());
        if (missing) {
            List<Recipe<?>> all = new ArrayList<>(manager.getRecipes());
            all.removeIf(existing -> RECIPES.values().stream().anyMatch(recipe -> existing.getId().equals(location(recipe))));
            RECIPES.values().forEach(recipe -> toVanilla(recipe).ifPresent(all::add));
            manager.replaceRecipes(all);
        }
        if (!players.isEmpty()) {
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(manager.getRecipes());
            players.forEach(player -> player.connection.send(packet));
        }
    }

    private static ResourceLocation location(ShapedRecipe recipe) {
        return new ResourceLocation(recipe.id());
    }

    private static Optional<Recipe<?>> toVanilla(ShapedRecipe recipe) {
        int height = recipe.pattern().size();
        int width = recipe.pattern().stream().mapToInt(String::length).max().orElse(0);
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int row = 0; row < height; row++) {
            String line = recipe.pattern().get(row);
            for (int column = 0; column < line.length(); column++) {
                char key = line.charAt(column);
                if (key == ' ') {
                    continue;
                }
                ItemStack stack = stackFor(recipe.keys().get(key), 1);
                if (stack.isEmpty()) {
                    return Optional.empty();
                }
                ingredients.set(row * width + column, Ingredient.of(stack));
            }
        }
        ItemStack result = stackFor(recipe.resultId(), recipe.count());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new net.minecraft.world.item.crafting.ShapedRecipe(location(recipe), "",
                CraftingBookCategory.EQUIPMENT, width, height, ingredients, result));
    }

    /** Стопка обычного предмета или предмета мода по ID. */
    static ItemStack stackFor(String id, int count) {
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Optional<CustomItem> custom = get(id);
        if (custom.isPresent()) {
            return create(custom.get(), count);
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location)
                ? new ItemStack(BuiltInRegistries.ITEM.get(location), count) : ItemStack.EMPTY;
    }
}
