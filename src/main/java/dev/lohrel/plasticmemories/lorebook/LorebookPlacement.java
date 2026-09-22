package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Where an entry goes in the prompt, decided from its source position and the app that made it. */
public enum LorebookPlacement {
    /** Before the character description. */
    BEFORE_CHARACTER,
    /** After the character description and card. */
    AFTER_CHARACTER,
    /** Inserted as its own chat message, {@code depth} messages from the end. */
    AT_DEPTH,
    /** A placement we don't implement (author's note, example messages, outlets...). The entry is skipped. */
    UNSUPPORTED;

    /**
     * SillyTavern numbers positions 0 before, 1 after, 2/3 author's note, 4 at depth, 5/6 example
     * messages, 7 outlet. Marinara uses 0 before, 1 after, 2 at depth, 7 outlet. Depth and role only
     * matter at depth, so they're ignored elsewhere.
     */
    public static LorebookPlacement of(CompatibilityProfile profile, LorebookInsertion insertion) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(insertion, "insertion");
        int atDepthPosition = profile == CompatibilityProfile.MARINARA ? 2 : 4;
        int position = insertion.sourcePosition();
        if (position == 0) {
            return BEFORE_CHARACTER;
        }
        if (position == 1) {
            return AFTER_CHARACTER;
        }
        if (position == atDepthPosition && insertion.role() != LorebookPromptRole.UNKNOWN) {
            return AT_DEPTH;
        }
        return UNSUPPORTED;
    }

    /** Placement, but also UNSUPPORTED for regex entries, which never match (Java and JS regex differ). */
    public static LorebookPlacement of(CompatibilityProfile profile, ImportedLorebookEntry entry) {
        return entry.matchOptions().regex() ? UNSUPPORTED : of(profile, entry.insertion());
    }
}
