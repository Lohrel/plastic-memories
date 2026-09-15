package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CookAvailabilityResolverTest {
    @Test
    void reportsAvailableForEligibleNpcWithFood() {
        CookAvailability availability = CookAvailabilityResolver.resolve(
                true, false, List.of(new CookFoodCandidate(2, 3, true)));

        assertEquals(CookAvailability.AVAILABLE, availability);
    }

    @Test
    void reportsNoFoodForEligibleNpcWithoutFood() {
        CookAvailability availability = CookAvailabilityResolver.resolve(
                true, false, List.of(new CookFoodCandidate(2, 3, false)));

        assertEquals(CookAvailability.NO_FOOD, availability);
    }

    @Test
    void reportsAvailableWhenNearbyContainerHasFood() {
        CookAvailability availability = CookAvailabilityResolver.resolve(
                true, false, List.of(new CookFoodCandidate(2, 3, false)), true);

        assertEquals(CookAvailability.AVAILABLE, availability);
    }

    @Test
    void reportsBusyWithoutDisclosingWhyNpcIsUnavailable() {
        assertEquals(CookAvailability.BUSY, CookAvailabilityResolver.resolve(
                false, false, List.of(new CookFoodCandidate(2, 3, true))));
        assertEquals(CookAvailability.BUSY, CookAvailabilityResolver.resolve(
                true, true, List.of(new CookFoodCandidate(2, 3, true))));
    }
}
