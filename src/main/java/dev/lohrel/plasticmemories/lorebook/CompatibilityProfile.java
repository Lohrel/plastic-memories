package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Which app's matching rules to follow: Marinara for native Marinara files, SillyTavern for everything else. */
public enum CompatibilityProfile {
    SILLY_TAVERN(false, false),
    MARINARA(false, false);

    private final boolean defaultCaseSensitive;
    private final boolean defaultWholeWord;

    CompatibilityProfile(boolean defaultCaseSensitive, boolean defaultWholeWord) {
        this.defaultCaseSensitive = defaultCaseSensitive;
        this.defaultWholeWord = defaultWholeWord;
    }

    public boolean defaultCaseSensitive() {
        return defaultCaseSensitive;
    }

    public boolean defaultWholeWord() {
        return defaultWholeWord;
    }

    public static CompatibilityProfile forFormat(LorebookSourceFormat format) {
        Objects.requireNonNull(format, "format");
        return format == LorebookSourceFormat.MARINARA_LOREBOOK
                || format == LorebookSourceFormat.MARINARA_CHARACTER
                ? MARINARA
                : SILLY_TAVERN;
    }

    /** Old libraries may contain removed profile names (e.g. Chub); those load as SillyTavern. */
    public static CompatibilityProfile fromPersistedName(String name) {
        return switch (Objects.requireNonNull(name, "name")) {
            case "MARINARA" -> MARINARA;
            case "SILLY_TAVERN", "CHUB", "PLASTIC_MEMORIES" -> SILLY_TAVERN;
            default -> throw new IllegalArgumentException("Unknown persisted compatibility profile.");
        };
    }
}
