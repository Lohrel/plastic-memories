package dev.lohrel.plasticmemories.server;

/** Decides how a COOK task begins: deliver carried food, search containers, or fail. */
final class CookStartPlanner {
    private CookStartPlanner() {
    }

    static Result resolve(boolean npcHasFood, int nearbyContainerCount) {
        if (npcHasFood) {
            return Result.DELIVER_NPC_FOOD;
        }
        return nearbyContainerCount > 0 ? Result.SEARCH_CONTAINERS : Result.NO_CONTAINER;
    }

    enum Result {
        DELIVER_NPC_FOOD,
        SEARCH_CONTAINERS,
        NO_CONTAINER
    }
}
