package dev.lohrel.plasticmemories.card;

import dev.lohrel.plasticmemories.lorebook.ImportedCharacterCard;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The cards folder plus each NPC's chosen card and edits. A binding to a file that's gone just means "no card". */
public final class CharacterCards {
    private final CharacterCardFolder folder;
    private final CardBindingStore bindings;

    public CharacterCards(CharacterCardFolder folder, CardBindingStore bindings) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    public CharacterCardFolder folder() {
        return folder;
    }

    public Optional<String> boundFile(LocalLorebookBindingKey key) {
        return bindings.boundFile(key);
    }

    /** The NPC's card with this player's edits applied. */
    public Optional<ImportedCharacterCard> boundCard(LocalLorebookBindingKey key) {
        return editableCard(key).map(EditableCard::card);
    }

    /** What the "My card" view shows: the card as edited, and which fields differ from the file. */
    public Optional<EditableCard> editableCard(LocalLorebookBindingKey key) {
        Optional<CharacterCardFolder.CardFile> file = bindings.boundFile(key).flatMap(folder::find);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Map<CardField, String> edits = bindings.edits(key);
        ImportedCharacterCard edited;
        try {
            edited = CardField.apply(file.orElseThrow().card(), edits);
        } catch (IllegalArgumentException exception) {
            // Edits that no longer fit the card's limits: show and use the file's text instead of failing.
            edited = file.orElseThrow().card();
            edits = Map.of();
        }
        return Optional.of(new EditableCard(
                file.orElseThrow().fileName(),
                edited,
                file.orElseThrow().card(),
                edits.keySet().isEmpty() ? Set.of() : EnumSet.copyOf(edits.keySet())));
    }

    /** Keeps only the fields that differ from the file, so unedited fields keep following it. */
    public void saveEdits(LocalLorebookBindingKey key, Map<CardField, String> values) throws IOException {
        Optional<CharacterCardFolder.CardFile> file = bindings.boundFile(key).flatMap(folder::find);
        if (file.isEmpty()) {
            return;
        }
        EnumMap<CardField, String> edits = new EnumMap<>(CardField.class);
        edits.putAll(bindings.edits(key));
        values.forEach((field, value) -> {
            if (value.equals(field.get(file.orElseThrow().card()))) {
                edits.remove(field);
            } else {
                edits.put(field, value);
            }
        });
        // Build it once so an over-long field fails here, not in the next prompt.
        CardField.apply(file.orElseThrow().card(), edits);
        bindings.saveEdits(key, edits);
    }

    public void resetEdits(LocalLorebookBindingKey key) throws IOException {
        bindings.saveEdits(key, Map.of());
    }

    public void bind(LocalLorebookBindingKey key, String fileName) throws IOException {
        bindings.bind(key, fileName);
    }

    public void unbind(LocalLorebookBindingKey key) throws IOException {
        bindings.unbind(key);
    }

    /**
     * @param card     the card with this player's edits applied
     * @param fromFile the card as it is in the file
     */
    public record EditableCard(String fileName, ImportedCharacterCard card, ImportedCharacterCard fromFile, Set<CardField> edited) {
    }
}
