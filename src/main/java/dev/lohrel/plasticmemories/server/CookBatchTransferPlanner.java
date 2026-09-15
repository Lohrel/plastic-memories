package dev.lohrel.plasticmemories.server;

import java.util.List;
import java.util.function.IntUnaryOperator;

public final class CookBatchTransferPlanner {
    private CookBatchTransferPlanner() {
    }

    public static CookBatchTransferPlan plan(
            List<CookFoodCandidate> candidates, int destinationCapacity, int maximumQuantity) {
        return plan(candidates, ignored -> destinationCapacity, maximumQuantity);
    }

    public static CookBatchTransferPlan plan(
            List<CookFoodCandidate> candidates,
            IntUnaryOperator destinationCapacityBySourceSlot,
            int maximumQuantity) {
        if (maximumQuantity <= 0) {
            throw new IllegalArgumentException("maximumQuantity must be positive");
        }
        boolean foundFood = false;
        for (CookFoodCandidate candidate : candidates) {
            if (!candidate.food() || candidate.count() <= 0) {
                continue;
            }
            foundFood = true;
            int destinationCapacity = destinationCapacityBySourceSlot.applyAsInt(candidate.slot());
            if (destinationCapacity > 0) {
                return CookBatchTransferPlan.ready(
                        candidate.slot(), Math.min(Math.min(candidate.count(), destinationCapacity), maximumQuantity));
            }
        }
        return CookBatchTransferPlan.failed(foundFood
                ? CookBatchTransferPlan.Result.INVENTORY_FULL
                : CookBatchTransferPlan.Result.NO_FOOD);
    }
}
