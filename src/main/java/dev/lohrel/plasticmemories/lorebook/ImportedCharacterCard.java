package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An imported character card. Stays on this client; it is separate from the NPC's shared
 * server profile ({@link dev.lohrel.plasticmemories.npc.NpcProfile}).
 */
public record ImportedCharacterCard(
        String name,
        String description,
        String personality,
        String scenario,
        String firstMessage,
        List<String> alternateGreetings,
        String exampleDialogue,
        String creator,
        String creatorVersion,
        List<String> tags,
        String systemPrompt,
        String postHistoryInstructions,
        String creatorNotes,
        String nickname,
        Optional<ImportedLorebook> embeddedLorebook,
        List<String> sourceMetadataJson,
        List<String> assetDescriptorsJson,
        String sourceExtensionsJson) {
    public static final int MAX_FIELD_LENGTH = 16_384;
    public static final int MAX_GREETING_COUNT = 64;
    public static final int MAX_TAG_COUNT = 64;
    public static final int MAX_METADATA_COUNT = 64;
    public static final int MAX_EXTENSION_LENGTH = 1_048_576;

    public ImportedCharacterCard {
        name = required(name, "name");
        description = bounded(description, "description");
        personality = bounded(personality, "personality");
        scenario = bounded(scenario, "scenario");
        firstMessage = bounded(firstMessage, "firstMessage");
        alternateGreetings = copyBounded(alternateGreetings, MAX_GREETING_COUNT, "alternateGreetings");
        exampleDialogue = bounded(exampleDialogue, "exampleDialogue");
        creator = bounded(creator, "creator");
        creatorVersion = bounded(creatorVersion, "creatorVersion");
        tags = copyBounded(tags, MAX_TAG_COUNT, "tags");
        systemPrompt = bounded(systemPrompt, "systemPrompt");
        postHistoryInstructions = bounded(postHistoryInstructions, "postHistoryInstructions");
        creatorNotes = bounded(creatorNotes, "creatorNotes");
        nickname = bounded(nickname, "nickname");
        embeddedLorebook = Objects.requireNonNull(embeddedLorebook, "embeddedLorebook");
        sourceMetadataJson = copyBounded(sourceMetadataJson, MAX_METADATA_COUNT, "sourceMetadataJson");
        assetDescriptorsJson = copyBounded(assetDescriptorsJson, MAX_METADATA_COUNT, "assetDescriptorsJson");
        sourceExtensionsJson = Objects.requireNonNull(sourceExtensionsJson, "sourceExtensionsJson");
        if (sourceExtensionsJson.length() > MAX_EXTENSION_LENGTH) {
            throw new IllegalArgumentException("Imported card source extensions are out of bounds.");
        }
    }

    /** For V1/V2 cards, which have no V3 extras. */
    public ImportedCharacterCard(
            String name,
            String description,
            String personality,
            String scenario,
            String firstMessage,
            List<String> alternateGreetings,
            String exampleDialogue,
            String creator,
            String creatorVersion,
            List<String> tags,
            String systemPrompt,
            String postHistoryInstructions,
            String creatorNotes,
            String nickname,
            Optional<ImportedLorebook> embeddedLorebook) {
        this(
                name,
                description,
                personality,
                scenario,
                firstMessage,
                alternateGreetings,
                exampleDialogue,
                creator,
                creatorVersion,
                tags,
                systemPrompt,
                postHistoryInstructions,
                creatorNotes,
                nickname,
                embeddedLorebook,
                List.of(),
                List.of(),
                "");
    }

    private static String required(String value, String field) {
        value = bounded(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Imported card " + field + " is required.");
        }
        return value;
    }

    private static String bounded(String value, String field) {
        value = Objects.requireNonNull(value, field);
        if (value.length() > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException("Imported card " + field + " is out of bounds.");
        }
        return value;
    }

    private static List<String> copyBounded(List<String> values, int maximumCount, String field) {
        values = List.copyOf(Objects.requireNonNull(values, field));
        if (values.size() > maximumCount) {
            throw new IllegalArgumentException("Imported card " + field + " has too many values.");
        }
        for (String value : values) {
            bounded(value, field);
        }
        return values;
    }
}
