package dev.lohrel.plasticmemories.persona;

import java.util.Objects;
import java.util.UUID;

/** Who the player is in a conversation, like a SillyTavern/Marinara persona. Its name replaces {{user}}. */
public record Persona(UUID id, String name, String description) {
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 8_192;

    public Persona {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(description, "description");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Persona name must be 1 to " + MAX_NAME_LENGTH + " characters.");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Persona description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
    }
}
