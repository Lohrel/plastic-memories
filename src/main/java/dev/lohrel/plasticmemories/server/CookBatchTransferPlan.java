package dev.lohrel.plasticmemories.server;

import java.util.OptionalInt;

public record CookBatchTransferPlan(Result result, OptionalInt sourceSlot, int quantity) {
    public static CookBatchTransferPlan ready(int sourceSlot, int quantity) {
        return new CookBatchTransferPlan(Result.READY, OptionalInt.of(sourceSlot), quantity);
    }

    public static CookBatchTransferPlan failed(Result result) {
        return new CookBatchTransferPlan(result, OptionalInt.empty(), 0);
    }

    public enum Result {
        READY,
        NO_FOOD,
        INVENTORY_FULL
    }
}
