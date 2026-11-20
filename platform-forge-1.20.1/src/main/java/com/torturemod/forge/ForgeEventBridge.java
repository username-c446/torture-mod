package com.torturemod.forge;

import net.minecraftforge.event.OnDatapackSyncEvent;
import com.torturemod.api.event.Cancellation;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.TargetId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Переводит события Forge в события ядра и применяет отмену обратно. */
final class ForgeEventBridge {
    private final Supplier<ForgePlatform> platform;
    private final Map<UUID, Boolean> crouching = new HashMap<>();
    /** Тик последнего действия рукой: взмах после него — не ЛКМ в воздух. */
    private final Map<UUID, Integer> lastHandAction = new HashMap<>();

    ForgeEventBridge(Supplier<ForgePlatform> platform) {
        this.platform = platform;
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ForgePlatform current = platform.get();
        if (current != null && event.getEntity() instanceof ServerPlayer player) {
            current.bodiesImpl().showTo(player);
            if (!(player instanceof BodyPlayer)) {
                SwingWatcher.attach(player.server, player, this::onSwing);
            }
        }
        fire(new GameEvent.PlayerJoined(id(event.getEntity())));
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        crouching.remove(event.getEntity().getUUID());
        lastHandAction.remove(event.getEntity().getUUID());
        fire(new GameEvent.PlayerLeft(id(event.getEntity())));
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.BlockBroken(id(player), ForgeWorlds.block(event.getLevel(), event.getPos()), cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
            }
        }
    }

    /** После /reload менеджер рецептов пересобран: рецепты мода добавляются снова. */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (platform.get() != null) {
            ForgeCustomItems.inject(event.getPlayerList().getServer(), event.getPlayer() != null
                    ? java.util.List.of(event.getPlayer()) : event.getPlayerList().getPlayers());
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && placesCustomItem(player)) {
            // Наручники — цепь с меткой: блоком их не ставят.
            event.setCanceled(true);
            resync(player);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.BlockPlaced(id(player), ForgeWorlds.block(event.getLevel(), event.getPos()), cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
                resync(player);
            }
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handAction(player);
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.EntityInteracted(id(player), id(event.getTarget()),
                    ForgeItems.id(event.getItemStack()), sneaking(player), cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                resync(player);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Cancellation cancellation = new Cancellation();
            handAction(player);
            fire(new GameEvent.BlockInteracted(id(player), ForgeWorlds.block(event.getLevel(), event.getPos()),
                    ForgeItems.id(event.getItemStack()), sneaking(player), cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                resync(player);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getItemStack().isEmpty()) {
            handAction(player);
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.ItemUsed(id(player), ForgeItems.id(event.getItemStack()).orElseThrow(), sneaking(player),
                    cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                resync(player);
            }
        }
    }

    /** ЛКМ по блоку: начало ломания. */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof BodyPlayer
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        handAction(player);
        Cancellation cancellation = new Cancellation();
        fire(new GameEvent.BlockAttacked(id(player), ForgeWorlds.block(event.getLevel(), event.getPos()),
                ForgeItems.id(player.getMainHandItem()), sneaking(player), cancellation));
        if (cancellation.isCancelled()) {
            event.setCanceled(true);
        }
    }

    /** Взмах без удара по блоку или сущности в этом тике — ЛКМ в воздух. */
    private void onSwing(ServerPlayer player) {
        Integer last = lastHandAction.get(player.getUUID());
        int now = player.server.getTickCount();
        if (last != null && now - last <= 1) {
            return;
        }
        fire(new GameEvent.ArmSwung(id(player), ForgeItems.id(player.getMainHandItem()), sneaking(player)));
    }

    private void handAction(ServerPlayer player) {
        lastHandAction.put(player.getUUID(), player.server.getTickCount());
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handAction(player);
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.AttackAttempted(id(player), id(event.getTarget()), cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            handAction(player);
            ItemStack stack = event.getEntity().getItem();
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.ItemDropped(id(player), ForgeItems.id(stack).orElse("minecraft:air"), cancellation));
            if (cancellation.isCancelled()) {
                // Отмена броска в Forge уничтожает предмет, поэтому возвращаем его в инвентарь.
                ItemStack back = stack.copy();
                player.getInventory().add(back);
                if (back.isEmpty()) {
                    event.setCanceled(true);
                } else {
                    event.getEntity().setItem(back);
                }
                resync(player);
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            fire(new GameEvent.Died(id(event.getEntity())));
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        Cancellation cancellation = new Cancellation();
        fire(new GameEvent.ChatSent(id(event.getPlayer()), event.getRawText(), cancellation));
        if (cancellation.isCancelled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            String command = event.getParseResults().getReader().getString();
            Cancellation cancellation = new Cancellation();
            fire(new GameEvent.CommandIssued(id(player), command.startsWith("/") ? command.substring(1) : command,
                    cancellation));
            if (cancellation.isCancelled()) {
                event.setCanceled(true);
            }
        }
    }

    /** Флаг Shift и поза приседа не всегда приходят вместе, поэтому присевшего считаем нажавшим Shift. */
    private static boolean sneaking(ServerPlayer player) {
        return player.isShiftKeyDown() || player.isCrouching();
    }

    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof BodyPlayer)) {
            fire(new GameEvent.InputPressed(id(player), GameEvent.Input.JUMP));
        }
    }

    /** Приседание приходит как смена позы, поэтому ловим момент, когда игрок присел. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player instanceof BodyPlayer) {
            return;
        }
        boolean now = sneaking(player);
        Boolean before = crouching.put(player.getUUID(), now);
        if (now && !Boolean.TRUE.equals(before)) {
            fire(new GameEvent.InputPressed(id(player), GameEvent.Input.SNEAK));
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Cancellation cancellation = new Cancellation();
        Entity source = event.getSource().getEntity();
        fire(new GameEvent.Damaged(id(event.getEntity()), Optional.ofNullable(source).map(ForgeEventBridge::id),
                event.getAmount(), cancellation));
        if (cancellation.isCancelled()) {
            event.setCanceled(true);
        }
    }

    /** Слезание с транспорта: пленный в конвое слезть сам не может. */
    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        Entity vehicle = event.getEntityBeingMounted();
        if (!event.isDismounting() || vehicle == null || vehicle.isRemoved() || event.getLevel().isClientSide()) {
            return;
        }
        Cancellation cancellation = new Cancellation();
        fire(new GameEvent.DismountAttempted(id(event.getEntityMounting()), id(vehicle), cancellation));
        if (cancellation.isCancelled()) {
            event.setCanceled(true);
            // Клиент уже показал, что игрок слез: напоминаем ему, кто где сидит.
            if (event.getEntityMounting() instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetPassengersPacket(vehicle));
            }
        }
    }

    private static boolean placesCustomItem(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (ForgeCustomItems.idOf(main).isPresent()) {
            return true;
        }
        return !(main.getItem() instanceof net.minecraft.world.item.BlockItem)
                && ForgeCustomItems.idOf(player.getOffhandItem()).isPresent();
    }

    private void fire(GameEvent event) {
        ForgePlatform current = platform.get();
        if (current != null) {
            current.gameEventsImpl().fire(event);
        }
    }

    private static TargetId id(Entity entity) {
        return ForgeEntities.idOf(entity);
    }

    /** Клиент без мода предсказывает действие сам, поэтому после отмены отправляем ему настоящий инвентарь. */
    private static void resync(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        player.containerMenu.sendAllDataToRemote();
    }
}
