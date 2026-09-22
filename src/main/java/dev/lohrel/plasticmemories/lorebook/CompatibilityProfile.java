package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Runtime defaults selected automatically from an external artifact's format. */
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

    /** Selects the only supported external runtime profile for a detected source format. */
    public static CompatibilityProfile forFormat(LorebookSourceFormat format) {
        Objects.requireNonNull(format, "format");
        return format == LorebookSourceFormat.MARINARA_LOREBOOK
                || format == LorebookSourceFormat.MARINARA_CHARACTER
                ? MARINARA
                : SILLY_TAVERN;
    }

    /** Reads persisted profile names, mapping removed profiles to the supported default. */
    public static CompatibilityProfile fromPersistedName(String name) {
        return switch (Objects.requireNonNull(name, "name")) {
            case "MARINARA" -> MARINARA;
            case "SILLY_TAVERN", "CHUB", "PLASTIC_MEMORIES" -> SILLY_TAVERN;
            default -> throw new IllegalArgumentException("Unknown persisted compatibility profile.");
        };
    }
}
