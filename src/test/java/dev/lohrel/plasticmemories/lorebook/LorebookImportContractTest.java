package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LorebookImportContractTest {
    @TempDir
    Path tempDir;

    @Test
    void automaticallyUsesSillyTavernForClassicWorldInfo() throws Exception {
        Path source = tempDir.resolve("forest-world.json");
        Files.writeString(source, """
                {
                  "entries": {
                    "forest": {
                      "key": ["forest", "woods"],
                      "content": "The forest is ancient.",
                      "order": 42
                    }
                  }
                }
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(LorebookSourceFormat.CLASSIC_WORLD_INFO, result.format());
        assertEquals(CompatibilityProfile.SILLY_TAVERN, result.profile());
        assertEquals("forest-world.json", result.sourceFilename());
        assertTrue(result.activationPossible());
        assertEquals(1, result.acceptedEntryCount());
        assertEquals(0, result.rejectedEntryCount());
        assertTrue(result.diagnostics().isEmpty());

        ImportedLorebookEntry entry = result.lorebook().orElseThrow().entries().getFirst();
        assertEquals("forest", entry.id());
        assertEquals(0, entry.sourceOrder());
        assertEquals(java.util.List.of("forest", "woods"), entry.primaryKeys());
        assertEquals(42, entry.order());
        assertFalse(entry.matchOptions().caseSensitive());
        assertFalse(entry.matchOptions().wholeWord());
    }

    @Test
    void preservesV2CardFieldsAndItsEmbeddedBookLocally() throws Exception {
        Path source = tempDir.resolve("warden.card.json");
        Files.writeString(source, """
                {
                  "spec": "chara_card_v2",
                  "data": {
                    "name": "Warden",
                    "description": "A careful guardian.",
                    "personality": "Reserved",
                    "scenario": "An ancient forest",
                    "first_mes": "State your purpose.",
                    "alternate_greetings": ["Halt."],
                    "mes_example": "<START>\\n{{char}}: Halt.",
                    "system_prompt": "Stay in character.",
                    "post_history_instructions": "Remember the forest.",
                    "creator": "Synthetic fixture",
                    "tags": ["guardian"],
                    "character_book": {
                      "entries": [{
                        "id": "forest",
                        "keys": ["forest"],
                        "content": "The forest is protected.",
                        "insertion_order": 50
                      }]
                    }
                  }
                }
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(LorebookSourceFormat.CHARACTER_CARD_V2, result.format());
        ImportedCharacterCard card = result.characterCard().orElseThrow();
        assertEquals("Warden", card.name());
        assertEquals("A careful guardian.", card.description());
        assertEquals("State your purpose.", card.firstMessage());
        assertEquals(java.util.List.of("Halt."), card.alternateGreetings());
        assertEquals("Stay in character.", card.systemPrompt());
        assertEquals("Remember the forest.", card.postHistoryInstructions());
        assertEquals(java.util.List.of("guardian"), card.tags());
        assertEquals(1, card.embeddedLorebook().orElseThrow().entries().size());
        assertTrue(result.activationPossible());
    }

    @Test
    void preservesInsertionTimedActivationAndRecursionControlsWithoutFlattening() throws Exception {
        Path source = tempDir.resolve("controls.json");
        Files.writeString(source, """
                {
                  "entries": [{
                    "id": "forest",
                    "keys": ["forest"],
                    "content": "The forest is protected.",
                    "position": 2,
                    "depth": 4,
                    "role": 1,
                    "outletName": "lore",
                    "probability": 75,
                    "sticky": 2,
                    "cooldown": 3,
                    "delay": 1,
                    "group": "forest-facts",
                    "groupWeight": 4,
                    "recursive": true,
                    "preventRecursion": true,
                    "excludeRecursion": false,
                    "delayUntilRecursion": true
                  }]
                }
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);
        ImportedLorebookEntry entry = result
                .lorebook()
                .orElseThrow()
                .entries()
                .getFirst();

        assertEquals(2, entry.insertion().sourcePosition());
        assertEquals(4, entry.insertion().depth());
        assertEquals(LorebookPromptRole.USER, entry.insertion().role());
        assertEquals("lore", entry.insertion().outletName());
        assertEquals(75, entry.activationState().probabilityPercent());
        assertEquals(2, entry.activationState().stickyTurns());
        assertEquals(3, entry.activationState().cooldownTurns());
        assertEquals(1, entry.activationState().delayTurns());
        assertEquals("forest-facts", entry.activationState().group());
        assertEquals(4, entry.activationState().groupWeight());
        assertTrue(entry.recursionOptions().recursive());
        assertTrue(entry.recursionOptions().preventRecursion());
        assertFalse(entry.recursionOptions().excludeRecursion());
        assertTrue(entry.recursionOptions().delayUntilRecursion());
        // Position 2 is SillyTavern's author's-note placement: kept, reported, and skipped on its own.
        assertTrue(result.activationPossible());
        assertEquals(1, result.skippedEntryCount());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == LorebookImportDiagnosticCode.UNSUPPORTED_FEATURE));
    }

    @Test
    void retainsTheGlobalRecursiveSweepLimit() throws Exception {
        Path source = tempDir.resolve("recursive-limit.json");
        Files.writeString(source, """
                {
                  "recursiveScanning": true,
                  "maxRecursionSteps": 2,
                  "entries": [{"keys": ["bessie"], "content": "Bessie knows Rufus."}]
                }
                """);

        ImportedLorebook book = LorebookImporter
                .importArtifact(source)
                .lorebook()
                .orElseThrow();

        assertTrue(book.recursiveScanning());
        assertEquals(2, book.maxRecursionSteps());
    }

    @Test
    void skipsRegexEntriesInsteadOfApproximatingJavaScriptRegex() throws Exception {
        Path source = tempDir.resolve("regex.json");
        Files.writeString(source, """
                {
                  "entries": [{
                    "keys": ["[a-z]+"],
                    "content": "Regex-only content.",
                    "useRegex": true
                  }]
                }
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(1, result.skippedEntryCount());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == LorebookImportDiagnosticCode.UNSUPPORTED_REGEX));
    }

    @Test
    void retainsV3MetadataAssetsAndBoundedUnknownExtensionsWithAVisibleWarning() throws Exception {
        Path source = tempDir.resolve("v3.json");
        Files.writeString(source, """
                {
                  "spec": "chara_card_v3",
                  "data": {
                    "name": "Warden",
                    "creator_notes": "Synthetic note.",
                    "nickname": "Ward",
                    "source": [{"name": "fixture-source"}],
                    "assets": [{"type": "icon", "uri": "local.png"}],
                    "future_field": {"preserve": true}
                  }
                }
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);
        ImportedCharacterCard card = result.characterCard().orElseThrow();

        assertEquals("Synthetic note.", card.creatorNotes());
        assertEquals("Ward", card.nickname());
        assertEquals(1, card.sourceMetadataJson().size());
        assertEquals(1, card.assetDescriptorsJson().size());
        assertTrue(card.sourceExtensionsJson().contains("future_field"));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == LorebookImportDiagnosticCode.UNKNOWN_SOURCE_EXTENSION));
        assertTrue(result.activationPossible());
    }

    @Test
    void importsTheCheckedInNativeMarinaraLorebookWithStringRoleNames() throws Exception {
        Path source = tempDir.resolve("marinara-native-basic.marinara.json");
        try (var input = LorebookImportContractTest.class.getResourceAsStream(
                "/lorebook/marinara-native-basic.marinara.json")) {
            assertTrue(input != null);
            Files.copy(input, source);
        }

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(LorebookSourceFormat.MARINARA_LOREBOOK, result.format());
        assertEquals(CompatibilityProfile.MARINARA, result.profile());
        assertTrue(result.activationPossible());
        assertEquals(1, result.acceptedEntryCount());
        assertEquals(0, result.rejectedEntryCount());
        assertEquals(
                LorebookPromptRole.SYSTEM,
                result.lorebook().orElseThrow().entries().getFirst().insertion().role());
    }

    @Test
    void importsACompatibleNativeMarinaraCharacterEnvelopeClientLocally() throws Exception {
        Path source = tempDir.resolve("native-character.json");
        Files.writeString(source, """
                {
                  "type": "marinara_character",
                  "version": 1,
                  "data": {
                    "name": "Native Warden",
                    "description": "A native fixture.",
                    "character_book": {"entries": [{
                      "id": "native-entry",
                      "keys": ["native"],
                      "content": "Native lore."
                    }]}
                  }
                }
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(LorebookSourceFormat.MARINARA_CHARACTER, result.format());
        assertEquals(CompatibilityProfile.MARINARA, result.profile());
        assertEquals("Native Warden", result.characterCard().orElseThrow().name());
        assertEquals(1, result.lorebook().orElseThrow().entries().size());
        assertTrue(result.activationPossible());
    }
}
