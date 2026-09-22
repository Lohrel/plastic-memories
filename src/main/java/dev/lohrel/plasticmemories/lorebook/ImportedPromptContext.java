package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The card and lore entries selected for one prompt. */
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
