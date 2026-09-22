package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Where the source app wanted this entry placed in the prompt (position, depth, role, outlet). */
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
