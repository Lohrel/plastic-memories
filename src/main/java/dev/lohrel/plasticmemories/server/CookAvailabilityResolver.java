package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.List;

public final class CookAvailabilityResolver {
    private CookAvailabilityResolver() {
    }

    public static CookAvailability resolve(
            boolean eligible, boolean busy, List<CookFoodCandidate> candidates) {
        return resolve(eligible, busy, candidates, false);
    }

    public static CookAvailability resolve(
            boolean eligible,
            boolean busy,
            List<CookFoodCandidate> candidates,
            boolean nearbyContainerHasFood) {
        if (!eligible || busy) {
            return CookAvailability.BUSY;
        }
        return CookFoodPlanner.selectSlot(candidates).isPresent() || nearbyContainerHasFood
                ? CookAvailability.AVAILABLE
                : CookAvailability.NO_FOOD;
    }
}
