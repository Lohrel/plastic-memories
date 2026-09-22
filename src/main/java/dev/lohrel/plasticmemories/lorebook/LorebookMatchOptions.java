package dev.lohrel.plasticmemories.lorebook;

/** How an entry's keys are matched (case, whole words, regex...), with profile defaults already applied. */
public record LorebookMatchOptions(boolean caseSensitive, boolean wholeWord, boolean regex, int scanDepth) {
    public static final int DEFAULT_SCAN_DEPTH = 3;
    public static final int MAX_SCAN_DEPTH = 128;

    public LorebookMatchOptions {
        if (scanDepth < 0 || scanDepth > MAX_SCAN_DEPTH) {
            throw new IllegalArgumentException("Lorebook scan depth must be between 0 and " + MAX_SCAN_DEPTH + ".");
        }
    }

    public static LorebookMatchOptions defaults(CompatibilityProfile profile) {
        return new LorebookMatchOptions(
                profile.defaultCaseSensitive(), profile.defaultWholeWord(), false, DEFAULT_SCAN_DEPTH);
    }
}
