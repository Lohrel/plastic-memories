package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookLibraryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesOnlyExplicitlyActivatedGlobalLoreAndTheLocallyBoundCardBookAfterReload() throws Exception {
        LorebookImportResult global = importJson("global.json", """
                {"entries":[{"id":"global","keys":["global"],"content":"Global lore."}]}
                """);
        LorebookImportResult card = importJson("card.json", """
                {
                  "spec":"chara_card_v2",
                  "data":{
                    "name":"Warden",
                    "character_book":{"entries":[{"id":"card","keys":["card"],"content":"Card lore."}]}
                  }
                }
                """);
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID globalId = store.store(global);
        UUID cardId = store.store(card);
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());

        assertTrue(store.activeContext(key).lorebooks().isEmpty());
        store.activateGlobal(globalId);
        store.bindCard(key, cardId);

        ClientLorebookContext context = new ClientLorebookLibraryStore(tempDir.resolve("library")).activeContext(key);
        assertEquals("Warden", context.card().orElseThrow().name());
        assertEquals(List.of("card", "global"), context.lorebooks().stream()
                .flatMap(book -> book.entries().stream())
                .map(ImportedLorebookEntry::id)
                .toList());
    }

    @Test
    void preservesTheConfiguredRecursiveSweepLimitAcrossClientLibraryReload() throws Exception {
        LorebookImportResult imported = importJson("recursive.json", """
                {
                  "recursiveScanning": true,
                  "maxRecursionSteps": 2,
                  "entries": [{"id":"bessie","keys":["bessie"],"content":"Bessie knows Rufus."}]
                }
                """);
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID id = store.store(imported);
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());
        store.activateGlobal(id);

        ImportedLorebook reloaded = new ClientLorebookLibraryStore(tempDir.resolve("library"))
                .activeContext(key)
                .lorebooks()
                .getFirst();

        assertEquals(2, reloaded.maxRecursionSteps());
    }

    @Test
    void migratesLegacyChubProfileToSillyTavernWhenReloadingLibrary() throws Exception {
        LorebookImportResult imported = importJson("legacy.json", """
                {"entries":[{"id":"legacy","keys":["legacy"],"content":"Legacy lore."}]}
                """);
        Path libraryDirectory = tempDir.resolve("legacy-library");
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(libraryDirectory);
        store.store(imported);

        Path stateFile = libraryDirectory.resolve("library.json");
        Files.writeString(stateFile, Files.readString(stateFile).replace("SILLY_TAVERN", "CHUB"));

        ClientLorebookLibraryStore reloaded = new ClientLorebookLibraryStore(libraryDirectory);
        assertEquals(CompatibilityProfile.SILLY_TAVERN, reloaded.listArtifacts().getFirst().profile());
        assertEquals(1, reloaded.listArtifacts().size());
    }

    private LorebookImportResult importJson(String name, String json) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, json);
        LorebookImportResult result = LorebookImporter.importArtifact(file);
        assertTrue(result.activationPossible());
        return result;
    }
}
