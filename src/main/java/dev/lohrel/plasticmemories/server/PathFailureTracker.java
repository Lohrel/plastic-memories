package dev.lohrel.plasticmemories.server;

/** Counts consecutive pathfinding failures; the task gives up on a target once the limit is hit. */
final class PathFailureTracker {
    private final int failureLimit;
    private int consecutiveFailures;

    PathFailureTracker(int failureLimit) {
        if (failureLimit <= 0) {
            throw new IllegalArgumentException("Failure limit must be positive");
        }
        this.failureLimit = failureLimit;
    }

    /** Returns true once the limit is reached. */
    boolean recordFailure() {
        consecutiveFailures++;
        return consecutiveFailures >= failureLimit;
    }

    void recordSuccess() {
        consecutiveFailures = 0;
    }
}
