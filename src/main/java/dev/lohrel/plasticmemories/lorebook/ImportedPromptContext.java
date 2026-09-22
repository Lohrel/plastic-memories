package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The card and lore entries selected for one prompt, highest priority first. */
public record ImportedPromptContext(
        Optional<ImportedCharacterCard> card,
        List<PlacedLoreEntry> lore) {
    public ImportedPromptContext {
        card = Objects.requireNonNull(card, "card");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
    }

    public static ImportedPromptContext empty() {
        return new ImportedPromptContext(Optional.empty(), List.of());
    }

    public List<ImportedLorebookEntry> loreEntries() {
        return lore.stream().map(PlacedLoreEntry::entry).toList();
    }

    public record PlacedLoreEntry(ImportedLorebookEntry entry, LorebookPlacement placement) {
        public PlacedLoreEntry {
            Objects.requireNonNull(entry, "entry");
            if (Objects.requireNonNull(placement, "placement") == LorebookPlacement.UNSUPPORTED) {
                throw new IllegalArgumentException("Unsupported entries are skipped before the prompt.");
            }
        }
    }
}
