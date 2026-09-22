package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.List;

/** Turns the server's view of an NPC into the COOK hint the client puts in the prompt. */
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
        // The client only needs to know "not now"; ineligible and busy both become BUSY.
        if (!eligible || busy) {
            return CookAvailability.BUSY;
        }
        return CookFoodPlanner.selectSlot(candidates).isPresent() || nearbyContainerHasFood
                ? CookAvailability.AVAILABLE
                : CookAvailability.NO_FOOD;
    }
}
