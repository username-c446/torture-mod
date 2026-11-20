package com.torturemod.forge;

import com.torturemod.api.model.Book;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Книга без выдачи предмета: на мгновение кладём её в руку клиенту, открываем и сразу возвращаем
 * настоящий предмет. На сервере в руке всё время лежит то, что было.
 */
final class ForgeBooks {
    private ForgeBooks() {
    }

    static void open(ServerPlayer player, Book book) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.getOrCreateTag().putString("title", book.title());
        stack.getOrCreateTag().putString("author", book.author());
        stack.getOrCreateTag().putBoolean("resolved", true);
        ListTag pages = new ListTag();
        for (Book.Page page : book.pages()) {
            MutableComponent text = Component.empty();
            for (Book.Span span : page.spans()) {
                MutableComponent part = Component.literal(span.text());
                if (span.bold()) {
                    part = part.withStyle(ChatFormatting.BOLD);
                }
                if (span.goToPage() > 0) {
                    part = part.withStyle(style -> style.withColor(ChatFormatting.DARK_BLUE).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, String.valueOf(span.goToPage()))));
                }
                text.append(part);
            }
            pages.add(StringTag.valueOf(Component.Serializer.toJson(text)));
        }
        stack.getOrCreateTag().put("pages", pages);
        int slot = player.getInventory().selected;
        ItemStack held = player.getInventory().getItem(slot);
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, held));
    }
}
