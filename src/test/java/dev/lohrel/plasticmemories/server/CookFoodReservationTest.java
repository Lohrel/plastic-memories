package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CookFoodReservationTest {
    @Test
    void choosesFirstExistingFoodSlot() {
        var candidates = List.of(
                new CookFoodCandidate(0, 2, false),
                new CookFoodCandidate(1, 3, true),
                new CookFoodCandidate(2, 1, true));

        assertEquals(1, CookFoodPlanner.selectSlot(candidates).orElseThrow());
    }

    @Test
    void emptyAndNonFoodCandidatesCannotStartCook() {
        var candidates = List.of(
                new CookFoodCandidate(0, 0, true),
                new CookFoodCandidate(1, 4, false));

        assertTrue(CookFoodPlanner.selectSlot(candidates).isEmpty());
    }
}
