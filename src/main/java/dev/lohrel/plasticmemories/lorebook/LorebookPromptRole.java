package dev.lohrel.plasticmemories.lorebook;

/** Role requested for a lore insertion region. UNKNOWN remains inactive until explicitly supported. */
public enum LorebookPromptRole {
    SYSTEM(0),
    USER(1),
    ASSISTANT(2),
    UNKNOWN(-1);

    private final int sourceValue;

    LorebookPromptRole(int sourceValue) {
        this.sourceValue = sourceValue;
    }

    public int sourceValue() {
        return sourceValue;
    }

    public static LorebookPromptRole fromSourceValue(int sourceValue) {
        for (LorebookPromptRole value : values()) {
            if (value.sourceValue == sourceValue) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
