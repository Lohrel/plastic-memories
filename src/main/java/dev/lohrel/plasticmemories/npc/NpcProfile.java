package dev.lohrel.plasticmemories.npc;

import java.util.Objects;

/** The shared character profile of an NPC, stored on the server and shown to every player. */
public record NpcProfile(String description, String personality, String appearance, String backstory) {
    public static final int MAX_DESCRIPTION_LENGTH = 1_024;
    public static final int MAX_PERSONALITY_LENGTH = 2_048;
    public static final int MAX_APPEARANCE_LENGTH = 1_024;
    public static final int MAX_BACKSTORY_LENGTH = 4_096;

    public NpcProfile {
        description = validate(description, MAX_DESCRIPTION_LENGTH, "description");
        personality = validate(personality, MAX_PERSONALITY_LENGTH, "personality");
        appearance = validate(appearance, MAX_APPEARANCE_LENGTH, "appearance");
        backstory = validate(backstory, MAX_BACKSTORY_LENGTH, "backstory");
    }

    public static NpcProfile create(
            String description, String personality, String appearance, String backstory) {
        return new NpcProfile(description, personality, appearance, backstory);
    }

    public static NpcProfile empty() {
        return create("", "", "", "");
    }

    private static String validate(String value, int maximumLength, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maximumLength + " characters");
        }
        return value;
    }
}
