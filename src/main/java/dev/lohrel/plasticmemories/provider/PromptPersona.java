package dev.lohrel.plasticmemories.provider;

import java.util.Objects;

/** The player as the prompt sees them: the persona's name (or the player's own name) and description. */
public record PromptPersona(String name, String description) {
    /** SillyTavern's default name when no persona is set. */
    public static final PromptPersona DEFAULT = new PromptPersona("User", "");

    public PromptPersona {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }
}
