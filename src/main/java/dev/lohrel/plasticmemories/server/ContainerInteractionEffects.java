package dev.lohrel.plasticmemories.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Plays the open/close lid animation and sounds while an NPC is searching a container. Visual only. */
final class ContainerInteractionEffects {
    private static final int CHEST_OPEN_EVENT = 1;

    private ContainerInteractionEffects() {
    }

    static boolean isOccupied(ServerLevel level, BlockPos position) {
        if (!isLoaded(level, position)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof ChestBlock) {
            return animationPositions(level, position, state).stream()
                    .filter(part -> isLoaded(level, part))
                    .anyMatch(part -> ChestBlockEntity.getOpenCount(level, part) > 0);
        }
        // Barrels don't expose an open count, but their OPEN block state is set while a player is using one.
        return state.getBlock() == Blocks.BARREL && state.getValue(net.minecraft.world.level.block.BarrelBlock.OPEN);
    }

    static void begin(ServerLevel level, BlockPos position) {
        if (!isLoaded(level, position)) {
            return;
        }
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof ChestBlock) {
            signalChest(level, position, state, true);
            play(level, position, SoundEvents.CHEST_OPEN);
        } else if (state.getBlock() == Blocks.BARREL) {
            play(level, position, SoundEvents.BARREL_OPEN);
        }
    }

    /** Re-sent every tick: if a real player opens or closes the chest meanwhile, vanilla resets the lid. */
    static void maintain(ServerLevel level, BlockPos position) {
        if (!isLoaded(level, position)) {
            return;
        }
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof ChestBlock) {
            signalChest(level, position, state, true);
        }
    }

    static void end(ServerLevel level, BlockPos position) {
        if (!isLoaded(level, position)) {
            return;
        }
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof ChestBlock) {
            signalChest(level, position, state, false);
            play(level, position, SoundEvents.CHEST_CLOSE);
        } else if (state.getBlock() == Blocks.BARREL) {
            play(level, position, SoundEvents.BARREL_CLOSE);
        }
    }

    private static void signalChest(ServerLevel level, BlockPos position, BlockState state, boolean opening) {
        for (BlockPos part : animationPositions(level, position, state)) {
            if (!isLoaded(level, part)) {
                continue;
            }
            BlockState partState = level.getBlockState(part);
            Block block = partState.getBlock();
            // Add the NPC on top of the players who really have it open, so we never close it on them.
            int realOpeners = ChestBlockEntity.getOpenCount(level, part);
            level.blockEvent(part, block, CHEST_OPEN_EVENT, opening ? realOpeners + 1 : realOpeners);
        }
    }

    private static java.util.List<BlockPos> animationPositions(
            ServerLevel level, BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return java.util.List.of(position);
        }
        BlockPos partner = position.relative(ChestBlock.getConnectedDirection(state));
        if (!isLoaded(level, partner)) {
            return java.util.List.of(position);
        }
        return java.util.List.of(position, partner);
    }

    private static void play(ServerLevel level, BlockPos position, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, position, sound, SoundSource.BLOCKS, 0.5F,
                level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    private static boolean isLoaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }
}
