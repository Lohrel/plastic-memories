package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Matched imported data for a single private prompt; it has no server or transport types. */
public record ImportedPromptContext(
        Optional<ImportedCharacterCard> card,
        List<ImportedLorebookEntry> loreEntries) {
    public ImportedPromptContext {
        card = Objects.requireNonNull(card, "card");
        loreEntries = List.copyOf(Objects.requireNonNull(loreEntries, "loreEntries"));
    }

    public static ImportedPromptContext empty() {
        return new ImportedPromptContext(Optional.empty(), List.of());
    }
}
