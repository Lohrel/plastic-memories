package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CookDistancePolicyTest {
    @Test
    void allowsNpcToReachTheOppositeCornerOfTheContainerSearchArea() {
        assertTrue(CookDistancePolicy.withinTaskRange(78.0 * 78.0));
    }

    @Test
    void interruptsWhenNpcAndRequesterSeparateBeyondEightyBlocks() {
        assertFalse(CookDistancePolicy.withinTaskRange(80.01 * 80.01));
    }
}
