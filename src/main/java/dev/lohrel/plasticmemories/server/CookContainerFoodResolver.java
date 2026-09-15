package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.npc.NpcHandle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class CookContainerFoodResolver {
    private CookContainerFoodResolver() {
    }

    public static boolean hasNearbyFood(ServerLevel level, NpcHandle npc, ServerPlayer player) {
        return NearbyContainerLocator.find(level, npc.entity()).stream()
                .map(position -> VanillaContainerAccess.resolveAccessible(level, position, player))
                .flatMap(java.util.Optional::stream)
                .map(container -> CookInventoryTransfer.planFoodMove(
                        container, npc.inventory(), npc.inventory().getContainerSize(), 4))
                .anyMatch(plan -> plan.result() == CookBatchTransferPlan.Result.READY);
    }
}
