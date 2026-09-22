package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Files shaped like real SillyTavern exports, including the fields it always writes. */
final class SillyTavernCompatibilityTest {
    @TempDir
    Path directory;

    @Test
    void cardWithSpecVersionAndCharacterBookIsAccepted() throws Exception {
        LorebookImportResult result = importJson("""
                {"spec":"chara_card_v2","spec_version":"2.0","data":{"name":"Warden","description":"Keeps the gate.",
                 "character_book":{"entries":[{"id":1,"keys":["gate"],"content":"The gate is old.","enabled":true,
                 "insertion_order":10,"position":"before_char","extensions":{}}]}}}
                """);

        assertTrue(result.activationPossible());
        assertEquals("Warden", result.characterCard().orElseThrow().name());
        assertEquals(1, result.characterCard().orElseThrow().embeddedLorebook().orElseThrow().entries().size());
    }

    @Test
    void cardBookEntriesReadTheirSettingsFromExtensions() throws Exception {
        LorebookImportResult result = importJson("""
                {"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"Warden",
                 "character_book":{"entries":[
                   {"id":1,"keys":["gate"],"content":"Depth lore.","enabled":true,"insertion_order":10,
                    "position":"before_char",
                    "extensions":{"position":4,"depth":2,"role":1,"probability":40,"useProbability":true,
                                  "group":"gate","group_weight":7,"sticky":2,"cooldown":3,"delay":1,
                                  "selectiveLogic":3,"exclude_recursion":true}},
                   {"id":2,"keys":["wall"],"content":"After lore.","enabled":true,"insertion_order":5,
                    "position":"after_char"}]}}}
                """);

        var entries = result.characterCard().orElseThrow().embeddedLorebook().orElseThrow().entries();
        ImportedLorebookEntry depth = entries.get(0);
        assertEquals(new LorebookInsertion(4, 2, LorebookPromptRole.USER, ""), depth.insertion());
        assertEquals(new LorebookActivationState(40, 2, 3, 1, "gate", 7), depth.activationState());
        assertEquals(SecondaryKeyLogic.AND_ALL, depth.secondaryKeyLogic());
        assertTrue(depth.recursionOptions().excludeRecursion());
        assertEquals(1, entries.get(1).insertion().sourcePosition());
    }

    @Test
    void probabilityIsIgnoredWhenUseProbabilityIsOff() throws Exception {
        LorebookImportResult result = importJson(worldInfo("\"probability\":10,\"useProbability\":false"));

        assertEquals(100, result.lorebook().orElseThrow().entries().getFirst().activationState().probabilityPercent());
    }

    @Test
    void depthIsIgnoredForBeforeCharacterEntries() throws Exception {
        LorebookImportResult result = importJson(worldInfo("\"position\":0,\"depth\":4,\"role\":null"));

        assertTrue(result.activationPossible());
        assertEquals(0, result.skippedEntryCount());
    }

    @Test
    void unsupportedPlacementsSkipOnlyThoseEntries() throws Exception {
        LorebookImportResult result = importJson("""
                {"entries":{
                  "0":{"uid":0,"key":["a"],"content":"Before.","position":0,"depth":4},
                  "1":{"uid":1,"key":["b"],"content":"At depth.","position":4,"depth":2,"role":2},
                  "2":{"uid":2,"key":["c"],"content":"Author's note.","position":2,"depth":4},
                  "3":{"uid":3,"key":["d"],"content":"Regex.","position":0,"useRegex":true}}}
                """);

        assertTrue(result.activationPossible());
        assertEquals(2, result.skippedEntryCount());
    }

    @Test
    void marinaraUsesItsOwnPositionNumbers() {
        LorebookInsertion two = new LorebookInsertion(2, 3, LorebookPromptRole.ASSISTANT, "");

        assertEquals(LorebookPlacement.AT_DEPTH, LorebookPlacement.of(CompatibilityProfile.MARINARA, two));
        assertEquals(LorebookPlacement.UNSUPPORTED, LorebookPlacement.of(CompatibilityProfile.SILLY_TAVERN, two));
        assertEquals(LorebookPlacement.AT_DEPTH, LorebookPlacement.of(
                CompatibilityProfile.SILLY_TAVERN, new LorebookInsertion(4, 3, LorebookPromptRole.SYSTEM, "")));
        assertEquals(LorebookPlacement.AFTER_CHARACTER, LorebookPlacement.of(
                CompatibilityProfile.SILLY_TAVERN, new LorebookInsertion(1, 4, LorebookPromptRole.SYSTEM, "")));
        assertEquals(LorebookPlacement.UNSUPPORTED, LorebookPlacement.of(
                CompatibilityProfile.SILLY_TAVERN, new LorebookInsertion(4, 3, LorebookPromptRole.UNKNOWN, "")));
    }

    private static String worldInfo(String extraFields) {
        return "{\"entries\":{\"0\":{\"uid\":0,\"key\":[\"dragon\"],\"keysecondary\":[],\"content\":\"Dragons.\","
                + "\"constant\":false,\"selective\":true,\"selectiveLogic\":0,\"order\":100,\"disable\":false,"
                + extraFields + "}}}";
    }

    private LorebookImportResult importJson(String json) throws Exception {
        Path file = directory.resolve("import-" + System.nanoTime() + ".json");
        Files.writeString(file, json);
        return LorebookImporter.importArtifact(file);
    }
}
