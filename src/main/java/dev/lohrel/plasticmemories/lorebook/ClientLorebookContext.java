package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The only local imported content eligible for one private provider request. */
public record ClientLorebookContext(Optional<ImportedCharacterCard> card, List<ImportedLorebook> lorebooks) {
    public ClientLorebookContext {
        card = Objects.requireNonNull(card, "card");
        lorebooks = List.copyOf(Objects.requireNonNull(lorebooks, "lorebooks"));
    }

    public static ClientLorebookContext empty() {
        return new ClientLorebookContext(Optional.empty(), List.of());
    }
}
