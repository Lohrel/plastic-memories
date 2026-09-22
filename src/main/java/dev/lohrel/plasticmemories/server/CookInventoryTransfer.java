package dev.lohrel.plasticmemories.server;

import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Moves food between two inventories (container -> NPC, NPC -> player). */
final class CookInventoryTransfer {
    private CookInventoryTransfer() {
    }

    static Result moveFood(Container source, Container destination, int destinationSlots, int maximumQuantity) {
        var plan = planFoodMove(source, destination, destinationSlots, maximumQuantity);
        if (plan.result() == CookBatchTransferPlan.Result.NO_FOOD) {
            return new Result(CookBatchTransferPlan.Result.NO_FOOD, 0);
        }
        if (plan.result() == CookBatchTransferPlan.Result.INVENTORY_FULL) {
            return new Result(CookBatchTransferPlan.Result.INVENTORY_FULL, 0);
        }

        int sourceSlot = plan.sourceSlot().orElseThrow();
        ItemStack sourceStack = source.getItem(sourceSlot);
        if (sourceStack.isEmpty()
                || !sourceStack.has(DataComponents.FOOD)
                || !source.canTakeItem(destination, sourceSlot, sourceStack)) {
            return new Result(CookBatchTransferPlan.Result.NO_FOOD, 0);
        }
        // Insert first, then remove only what actually fit, so a failure can never delete items.
        int inserted = insert(destination, destinationSlots, sourceStack, plan.quantity());
        if (inserted <= 0) {
            return new Result(CookBatchTransferPlan.Result.INVENTORY_FULL, 0);
        }
        sourceStack.shrink(inserted);
        source.setChanged();
        return new Result(CookBatchTransferPlan.Result.READY, inserted);
    }

    static boolean hasFood(Container container) {
        return CookFoodPlanner.selectSlot(foodCandidates(container)).isPresent();
    }

    static CookBatchTransferPlan planFoodMove(
            Container source, Container destination, int destinationSlots, int maximumQuantity) {
        return CookBatchTransferPlanner.plan(
                foodCandidates(source),
                sourceSlot -> destinationCapacity(destination, destinationSlots, source.getItem(sourceSlot)),
                maximumQuantity);
    }

    static ArrayList<CookFoodCandidate> foodCandidates(Container container) {
        int size = container.getContainerSize();
        var candidates = new ArrayList<CookFoodCandidate>(size);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getItem(slot);
            candidates.add(new CookFoodCandidate(slot, stack.getCount(), stack.has(DataComponents.FOOD)));
        }
        return candidates;
    }

    private static int destinationCapacity(Container destination, int destinationSlots, ItemStack sample) {
        if (sample.isEmpty()) {
            return 0;
        }
        int capacity = 0;
        int slots = Math.min(destinationSlots, destination.getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            if (!destination.canPlaceItem(slot, sample)) {
                continue;
            }
            ItemStack existing = destination.getItem(slot);
            int slotMaximum = Math.min(sample.getMaxStackSize(), destination.getMaxStackSize(sample));
            if (existing.isEmpty()) {
                capacity += slotMaximum;
            } else if (ItemStack.isSameItemSameComponents(existing, sample)) {
                capacity += Math.max(0, slotMaximum - existing.getCount());
            }
        }
        return capacity;
    }

    /** Tops up matching stacks first, then uses empty slots. Returns how many items were placed. */
    private static int insert(Container destination, int destinationSlots, ItemStack sample, int requested) {
        int remaining = requested;
        int slots = Math.min(destinationSlots, destination.getContainerSize());
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack existing = destination.getItem(slot);
            if (existing.isEmpty()
                    || !destination.canPlaceItem(slot, sample)
                    || !ItemStack.isSameItemSameComponents(existing, sample)) {
                continue;
            }
            int slotMaximum = Math.min(existing.getMaxStackSize(), destination.getMaxStackSize(existing));
            int moved = Math.min(remaining, Math.max(0, slotMaximum - existing.getCount()));
            existing.grow(moved);
            remaining -= moved;
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (!destination.getItem(slot).isEmpty() || !destination.canPlaceItem(slot, sample)) {
                continue;
            }
            int slotMaximum = Math.min(sample.getMaxStackSize(), destination.getMaxStackSize(sample));
            int moved = Math.min(remaining, slotMaximum);
            destination.setItem(slot, sample.copyWithCount(moved));
            remaining -= moved;
        }
        int inserted = requested - remaining;
        if (inserted > 0) {
            destination.setChanged();
        }
        return inserted;
    }

    record Result(CookBatchTransferPlan.Result result, int quantity) {
    }
}
