package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CookStartPlannerTest {
    @Test
    void searchesContainersWhenNpcHasNoFoodButContainersExist() {
        assertEquals(CookStartPlanner.Result.SEARCH_CONTAINERS, CookStartPlanner.resolve(false, 3));
    }

    @Test
    void deliversNpcFoodBeforeSearchingContainers() {
        assertEquals(CookStartPlanner.Result.DELIVER_NPC_FOOD, CookStartPlanner.resolve(true, 3));
    }

    @Test
    void reportsNoContainerWhenNpcHasNoFoodAndNothingCanBeSearched() {
        assertEquals(CookStartPlanner.Result.NO_CONTAINER, CookStartPlanner.resolve(false, 0));
    }
}
