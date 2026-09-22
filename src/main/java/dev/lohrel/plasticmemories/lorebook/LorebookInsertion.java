package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Preserved source insertion settings; runtime chooses a named safe region from them. */
public record LorebookInsertion(
        int sourcePosition,
        int depth,
        LorebookPromptRole role,
        String outletName) {
    public static final LorebookInsertion DEFAULT = new LorebookInsertion(0, 0, LorebookPromptRole.SYSTEM, "");

    public LorebookInsertion {
        role = Objects.requireNonNull(role, "role");
        outletName = Objects.requireNonNull(outletName, "outletName");
        if (sourcePosition < 0 || sourcePosition > 16 || depth < 0 || depth > 256 || outletName.length() > 256) {
            throw new IllegalArgumentException("Invalid lorebook insertion settings.");
        }
    }
}
