package dev.lohrel.plasticmemories.lorebook;

/**
 * SillyTavern selective-key logic values. Primary key matching is always
 * required for non-constant entries; this rule applies only to secondary keys.
 */
public enum SecondaryKeyLogic {
    AND_ANY(0),
    NOT_ALL(1),
    NOT_ANY(2),
    AND_ALL(3);

    private final int sillyTavernValue;

    SecondaryKeyLogic(int sillyTavernValue) {
        this.sillyTavernValue = sillyTavernValue;
    }

    public int sillyTavernValue() {
        return sillyTavernValue;
    }

    public static SecondaryKeyLogic fromSillyTavernValue(int value) {
        for (SecondaryKeyLogic logic : values()) {
            if (logic.sillyTavernValue == value) {
                return logic;
            }
        }
        return AND_ANY;
    }
}
