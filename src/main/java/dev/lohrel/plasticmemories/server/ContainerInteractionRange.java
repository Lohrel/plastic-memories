package dev.lohrel.plasticmemories.server;

/**
 * Distances for using a container. The NPC must get within 2 blocks to open one, but can drift
 * out to 3 blocks while searching it, so small nudges don't cancel the search.
 */
final class ContainerInteractionRange {
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.0 * 2.0;
    private static final double RETENTION_DISTANCE_SQUARED = 3.0 * 3.0;

    private ContainerInteractionRange() {
    }

    static boolean isWithinArrivalDistance(double distanceSquared) {
        return distanceSquared <= ARRIVAL_DISTANCE_SQUARED;
    }

    static boolean isWithinRetentionDistance(double distanceSquared) {
        return distanceSquared <= RETENTION_DISTANCE_SQUARED;
    }
}
