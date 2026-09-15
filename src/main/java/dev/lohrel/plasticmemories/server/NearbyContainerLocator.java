package dev.lohrel.plasticmemories.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;

final class NearbyContainerLocator {
    static final int HORIZONTAL_RANGE = 32;
    static final int VERTICAL_RANGE = 8;
    static final int MAX_VISITED_CONTAINERS = 10;

    private NearbyContainerLocator() {
    }

    static List<BlockPos> find(ServerLevel level, Mob npc) {
        BlockPos center = npc.blockPosition();
        int minChunkX = (center.getX() - HORIZONTAL_RANGE) >> 4;
        int maxChunkX = (center.getX() + HORIZONTAL_RANGE) >> 4;
        int minChunkZ = (center.getZ() - HORIZONTAL_RANGE) >> 4;
        int maxChunkZ = (center.getZ() + HORIZONTAL_RANGE) >> 4;
        Set<Long> seen = new HashSet<>();
        List<ContainerCandidate> candidates = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos position : List.copyOf(chunk.getBlockEntities().keySet())) {
                    int dx = position.getX() - center.getX();
                    int dy = position.getY() - center.getY();
                    int dz = position.getZ() - center.getZ();
                    if (Math.abs(dx) > HORIZONTAL_RANGE
                            || Math.abs(dy) > VERTICAL_RANGE
                            || Math.abs(dz) > HORIZONTAL_RANGE) {
                        continue;
                    }
                    BlockState state = level.getBlockState(position);
                    if (!supports(state.getBlock())) {
                        continue;
                    }
                    BlockPos canonical = canonicalPosition(position, state);
                    if (!seen.add(canonical.asLong())) {
                        continue;
                    }
                    candidates.add(new ContainerCandidate(
                            canonical.getX(), canonical.getY(), canonical.getZ(),
                            npc.distanceToSqr(canonical.getCenter())));
                }
            }
        }

        return ContainerCandidatePlanner.nearest(candidates, MAX_VISITED_CONTAINERS).stream()
                .map(candidate -> new BlockPos(candidate.x(), candidate.y(), candidate.z()))
                .toList();
    }

    static boolean supports(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL;
    }

    private static BlockPos canonicalPosition(BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return position.immutable();
        }
        BlockPos partner = position.relative(ChestBlock.getConnectedDirection(state));
        return position.asLong() <= partner.asLong() ? position.immutable() : partner.immutable();
    }
}
