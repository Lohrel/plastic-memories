package dev.lohrel.plasticmemories.server;

final class CookDistancePolicy {
    private static final double MAXIMUM_DISTANCE_SQUARED = 80.0 * 80.0;

    private CookDistancePolicy() {
    }

    static boolean withinTaskRange(double distanceSquared) {
        return distanceSquared <= MAXIMUM_DISTANCE_SQUARED;
    }
}
