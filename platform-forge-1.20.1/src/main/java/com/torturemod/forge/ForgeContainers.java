package com.torturemod.forge;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.port.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Сундуки и бочки мира. Двойной сундук берётся целиком, как его видит игрок. */
final class ForgeContainers implements Containers {
    private final MinecraftServer server;

    ForgeContainers(MinecraftServer server) {
        this.server = server;
    }

    private Optional<Container> container(BlockLocation block) {
        ServerLevel level = ForgeWorlds.level(server, block.worldId());
        BlockPos pos = ForgeWorlds.pos(block);
        if (level == null || !level.isLoaded(pos)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return Optional.ofNullable(ChestBlock.getContainer(chest, state, level, pos, true));
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    @Override
    public Optional<List<ItemStackData>> contents(BlockLocation block) {
        return container(block).map(container -> {
            List<ItemStackData> stacks = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ForgeStacks.encode(container.getItem(slot)).ifPresent(stacks::add);
            }
            return stacks;
        });
    }

    @Override
    public List<ItemStackData> insert(BlockLocation block, List<ItemStackData> stacks) {
        Optional<Container> container = container(block);
        if (container.isEmpty()) {
            return List.copyOf(stacks);
        }
        List<ItemStackData> left = new ArrayList<>();
        for (ItemStackData data : stacks) {
            for (ItemStack stack : ForgeStacks.decode(data)) {
                ItemStack rest = HopperBlockEntity.addItem(null, container.get(), stack, null);
                ForgeStacks.encode(rest).ifPresent(left::add);
            }
        }
        container.get().setChanged();
        return left;
    }

    @Override
    public List<ItemStackData> remove(BlockLocation block, String itemId, int count) {
        List<ItemStackData> taken = new ArrayList<>();
        container(block).ifPresent(container -> {
            int need = count;
            for (int slot = 0; slot < container.getContainerSize() && need > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (ForgeStacks.encode(stack).filter(data -> data.itemId().equals(itemId)).isEmpty()) {
                    continue;
                }
                ItemStack part = container.removeItem(slot, Math.min(need, stack.getCount()));
                need -= part.getCount();
                ForgeStacks.encode(part).ifPresent(taken::add);
            }
            container.setChanged();
        });
        return taken;
    }

    @Override
    public List<ItemStackData> takeStacks(BlockLocation block, int maxStacks) {
        List<ItemStackData> taken = new ArrayList<>();
        container(block).ifPresent(container -> {
            for (int slot = 0; slot < container.getContainerSize() && taken.size() < maxStacks; slot++) {
                ForgeStacks.encode(container.removeItemNoUpdate(slot)).ifPresent(taken::add);
            }
            container.setChanged();
        });
        return taken;
    }
}
