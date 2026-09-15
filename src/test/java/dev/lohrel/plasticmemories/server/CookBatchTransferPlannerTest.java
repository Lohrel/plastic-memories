package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CookBatchTransferPlannerTest {
    @Test
    void choosesFirstFoodStackThatFitsDestination() {
        CookBatchTransferPlan plan = CookBatchTransferPlanner.plan(
                List.of(
                        new CookFoodCandidate(1, 4, true),
                        new CookFoodCandidate(2, 3, true)),
                slot -> slot == 1 ? 0 : 3,
                4);

        assertEquals(CookBatchTransferPlan.Result.READY, plan.result());
        assertEquals(2, plan.sourceSlot().orElseThrow());
        assertEquals(3, plan.quantity());
    }

    @Test
    void capsAContainerPickupAtFourItems() {
        CookBatchTransferPlan plan = CookBatchTransferPlanner.plan(
                List.of(new CookFoodCandidate(3, 12, true)), 20, 4);

        assertEquals(CookBatchTransferPlan.Result.READY, plan.result());
        assertEquals(3, plan.sourceSlot().orElseThrow());
        assertEquals(4, plan.quantity());
    }
}
