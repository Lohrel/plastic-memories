package dev.lohrel.plasticmemories.server;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Returns a container's inventory only if the requesting player could open it themselves:
 * chunk loaded, world permission (spawn protection etc.), not locked, and not blocked.
 */
final class VanillaContainerAccess {
    private VanillaContainerAccess() {
    }

    static Optional<Container> resolveAccessible(
            ServerLevel level, BlockPos position, Player requestingPlayer) {
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null
                || !level.mayInteract(requestingPlayer, position)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(position);
        Block block = state.getBlock();
        if (!NearbyContainerLocator.supports(block)) {
            return Optional.empty();
        }
        BlockEntity selectedEntity = level.getBlockEntity(position);
        if (!isUnlocked(level, selectedEntity)) {
            return Optional.empty();
        }

        if (block == Blocks.BARREL) {
            return selectedEntity instanceof Container container ? Optional.of(container) : Optional.empty();
        }
        if (!(block instanceof ChestBlock chestBlock) || ChestBlock.isChestBlockedAt(level, position)) {
            return Optional.empty();
        }
        // A double chest is only usable if the other half passes the same checks.
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos partner = position.relative(ChestBlock.getConnectedDirection(state));
            boolean partnerChunkLoaded = level.getChunkSource().getChunkNow(
                    partner.getX() >> 4, partner.getZ() >> 4) != null;
            if (!partnerChunkLoaded
                    || !level.mayInteract(requestingPlayer, partner)
                    || !isUnlocked(level, level.getBlockEntity(partner))) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(ChestBlock.getContainer(chestBlock, state, level, position, false));
    }

    private static boolean isUnlocked(ServerLevel level, BlockEntity blockEntity) {
        if (!(blockEntity instanceof BaseContainerBlockEntity container)) {
            return false;
        }
        LockCode lock = LockCode.fromTag(container.saveWithoutMetadata(level.registryAccess()));
        return lock.equals(LockCode.NO_LOCK);
    }
}
