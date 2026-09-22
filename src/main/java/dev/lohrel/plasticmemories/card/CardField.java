package dev.lohrel.plasticmemories.card;

import dev.lohrel.plasticmemories.lorebook.ImportedCharacterCard;
import java.util.Map;
import java.util.function.Function;

/** Card fields a player can edit in game (the "My card" tabs). Edits are stored per NPC, never in the file. */
public enum CardField {
    DESCRIPTION("description", "Description", ImportedCharacterCard::description),
    PERSONALITY("personality", "Personality", ImportedCharacterCard::personality),
    SCENARIO("scenario", "Scenario", ImportedCharacterCard::scenario),
    FIRST_MESSAGE("firstMessage", "First message", ImportedCharacterCard::firstMessage),
    EXAMPLE_DIALOGUE("exampleDialogue", "Examples", ImportedCharacterCard::exampleDialogue),
    SYSTEM_PROMPT("systemPrompt", "System prompt", ImportedCharacterCard::systemPrompt),
    POST_HISTORY_INSTRUCTIONS("postHistoryInstructions", "Post-history", ImportedCharacterCard::postHistoryInstructions);

    private final String storedName;
    private final String label;
    private final Function<ImportedCharacterCard, String> getter;

    CardField(String storedName, String label, Function<ImportedCharacterCard, String> getter) {
        this.storedName = storedName;
        this.label = label;
        this.getter = getter;
    }

    /** Key in card-bindings.json. */
    public String storedName() {
        return storedName;
    }

    public String label() {
        return label;
    }

    public String get(ImportedCharacterCard card) {
        return getter.apply(card);
    }

    public static CardField fromStoredName(String name) {
        for (CardField field : values()) {
            if (field.storedName.equals(name)) {
                return field;
            }
        }
        return null;
    }

    /** The card with these fields replaced; everything else (name, book, metadata) is kept. */
    public static ImportedCharacterCard apply(ImportedCharacterCard card, Map<CardField, String> edits) {
        if (edits.isEmpty()) {
            return card;
        }
        return new ImportedCharacterCard(
                card.name(),
                edits.getOrDefault(DESCRIPTION, card.description()),
                edits.getOrDefault(PERSONALITY, card.personality()),
                edits.getOrDefault(SCENARIO, card.scenario()),
                edits.getOrDefault(FIRST_MESSAGE, card.firstMessage()),
                card.alternateGreetings(),
                edits.getOrDefault(EXAMPLE_DIALOGUE, card.exampleDialogue()),
                card.creator(),
                card.creatorVersion(),
                card.tags(),
                edits.getOrDefault(SYSTEM_PROMPT, card.systemPrompt()),
                edits.getOrDefault(POST_HISTORY_INSTRUCTIONS, card.postHistoryInstructions()),
                card.creatorNotes(),
                card.nickname(),
                card.embeddedLorebook(),
                card.sourceMetadataJson(),
                card.assetDescriptorsJson(),
                card.sourceExtensionsJson());
    }
}
