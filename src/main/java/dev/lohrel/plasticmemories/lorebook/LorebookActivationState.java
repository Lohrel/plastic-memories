package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Timed/group activation data retained separately from keyword matching. */
public record LorebookActivationState(
        int probabilityPercent,
        int stickyTurns,
        int cooldownTurns,
        int delayTurns,
        String group,
        int groupWeight) {
    public static final LorebookActivationState DEFAULT = new LorebookActivationState(100, 0, 0, 0, "", 1);

    public LorebookActivationState {
        group = Objects.requireNonNull(group, "group");
        if (probabilityPercent < 0 || probabilityPercent > 100
                || stickyTurns < 0 || stickyTurns > 10_000
                || cooldownTurns < 0 || cooldownTurns > 10_000
                || delayTurns < 0 || delayTurns > 10_000
                || group.length() > 256 || groupWeight < 0 || groupWeight > 10_000) {
            throw new IllegalArgumentException("Invalid lorebook activation settings.");
        }
    }
}
