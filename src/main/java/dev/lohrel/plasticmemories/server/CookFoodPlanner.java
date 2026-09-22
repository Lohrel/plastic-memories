package dev.lohrel.plasticmemories.server;

import java.util.List;
import java.util.OptionalInt;

/** Picks the first inventory slot that holds food. */
public final class CookFoodPlanner {
    private CookFoodPlanner() {
    }

    public static OptionalInt selectSlot(List<CookFoodCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate.food() && candidate.count() > 0)
                .mapToInt(CookFoodCandidate::slot)
                .findFirst();
    }
}
