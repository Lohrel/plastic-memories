package dev.lohrel.plasticmemories.server;

final class PathFailureTracker {
    private final int failureLimit;
    private int consecutiveFailures;

    PathFailureTracker(int failureLimit) {
        if (failureLimit <= 0) {
            throw new IllegalArgumentException("Failure limit must be positive");
        }
        this.failureLimit = failureLimit;
    }

    boolean recordFailure() {
        consecutiveFailures++;
        return consecutiveFailures >= failureLimit;
    }

    void recordSuccess() {
        consecutiveFailures = 0;
    }
}
