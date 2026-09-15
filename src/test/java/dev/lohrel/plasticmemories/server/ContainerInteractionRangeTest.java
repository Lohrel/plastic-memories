package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ContainerInteractionRangeTest {
    @Test
    void keepsInteractingAfterMinorDriftBeyondArrivalDistance() {
        assertTrue(ContainerInteractionRange.isWithinArrivalDistance(4.0));
        assertFalse(ContainerInteractionRange.isWithinArrivalDistance(4.01));

        assertTrue(ContainerInteractionRange.isWithinRetentionDistance(6.25));
        assertFalse(ContainerInteractionRange.isWithinRetentionDistance(9.01));
    }
}
