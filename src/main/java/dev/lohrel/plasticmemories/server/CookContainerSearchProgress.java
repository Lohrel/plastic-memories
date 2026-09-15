package dev.lohrel.plasticmemories.server;

import java.util.OptionalInt;

final class CookContainerSearchProgress {
    private final int candidateCount;
    private final int interactionTicks;
    private int currentIndex;
    private long inspectAtTick;
    private Phase phase;

    CookContainerSearchProgress(int candidateCount, int interactionTicks) {
        if (candidateCount < 0 || interactionTicks < 0) {
            throw new IllegalArgumentException("counts and durations must not be negative");
        }
        this.candidateCount = candidateCount;
        this.interactionTicks = interactionTicks;
        this.phase = candidateCount == 0 ? Phase.EXHAUSTED : Phase.MOVING;
    }

    Phase phase() {
        return phase;
    }

    OptionalInt currentIndex() {
        return phase == Phase.MOVING || phase == Phase.EXAMINING
                ? OptionalInt.of(currentIndex)
                : OptionalInt.empty();
    }

    void arrived(long gameTick) {
        requirePhase(Phase.MOVING);
        phase = Phase.EXAMINING;
        inspectAtTick = gameTick + interactionTicks;
    }

    boolean readyToInspect(long gameTick) {
        return phase == Phase.EXAMINING && gameTick >= inspectAtTick;
    }

    void inspected(boolean pickedUpFood) {
        requirePhase(Phase.EXAMINING);
        if (pickedUpFood) {
            phase = Phase.CARRYING;
            return;
        }
        advance();
    }

    void pathFailed() {
        requirePhase(Phase.MOVING);
        advance();
    }

    void interactionInterrupted() {
        requirePhase(Phase.EXAMINING);
        phase = Phase.MOVING;
    }

    private void advance() {
        currentIndex++;
        phase = currentIndex < candidateCount ? Phase.MOVING : Phase.EXHAUSTED;
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected " + expected + " but was " + phase);
        }
    }

    enum Phase {
        MOVING,
        EXAMINING,
        CARRYING,
        EXHAUSTED
    }
}
