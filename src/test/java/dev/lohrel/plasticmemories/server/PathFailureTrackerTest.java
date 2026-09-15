package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PathFailureTrackerTest {
    @Test
    void toleratesFourFailuresAndStopsOnTheFifth() {
        PathFailureTracker tracker = new PathFailureTracker(5);

        assertFalse(tracker.recordFailure());
        assertFalse(tracker.recordFailure());
        assertFalse(tracker.recordFailure());
        assertFalse(tracker.recordFailure());
        assertTrue(tracker.recordFailure());
    }

    @Test
    void successfulPathAttemptResetsTheFailureSequence() {
        PathFailureTracker tracker = new PathFailureTracker(3);

        assertFalse(tracker.recordFailure());
        assertFalse(tracker.recordFailure());
        tracker.recordSuccess();

        assertFalse(tracker.recordFailure());
        assertFalse(tracker.recordFailure());
        assertTrue(tracker.recordFailure());
    }
}
