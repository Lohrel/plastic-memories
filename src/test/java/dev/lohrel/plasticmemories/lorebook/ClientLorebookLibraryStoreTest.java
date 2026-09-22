package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookLibraryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesOnlyExplicitlyActivatedLoreAfterReload() throws Exception {
        LorebookImportResult global = importJson("global.json", """
                {"entries":[{"id":"global","keys":["global"],"content":"Global lore."}]}
                """);
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID globalId = store.store(global);
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());

        assertTrue(store.activeContext(key).lorebooks().isEmpty());
        store.activateGlobal(globalId);

        ClientLorebookContext context = new ClientLorebookLibraryStore(tempDir.resolve("library")).activeContext(key);
        assertTrue(context.card().isEmpty());
        assertEquals(List.of("global"), context.lorebooks().stream()
                .flatMap(book -> book.entries().stream())
                .map(ImportedLorebookEntry::id)
                .toList());
    }

    @Test
    void characterCardsAreNotStoredInTheLibrary() throws Exception {
        LorebookImportResult card = importJson("card.json", """
                {"spec":"chara_card_v2","data":{"name":"Warden"}}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new ClientLorebookLibraryStore(tempDir.resolve("library")).store(card));
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

    @Test
    void unreadableLibraryIsSetAsideInsteadOfBeingOverwrittenByTheNextImport() throws Exception {
        Path libraryDirectory = tempDir.resolve("library");
        Files.createDirectories(libraryDirectory);
        Files.writeString(libraryDirectory.resolve("library.json"), "{\"version\":99,\"artifacts\":[]}");
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(libraryDirectory);

        assertTrue(store.listArtifacts().isEmpty());
        store.store(importJson("new.json", """
                {"entries":[{"id":"new","keys":["new"],"content":"New lore."}]}
                """));

        try (var files = Files.list(libraryDirectory)) {
            List<Path> setAside = files
                    .filter(path -> path.getFileName().toString().startsWith("library.json.unreadable-"))
                    .toList();
            assertEquals(1, setAside.size());
            assertEquals("{\"version\":99,\"artifacts\":[]}", Files.readString(setAside.getFirst()));
        }
    }

    // Frozen copy of the v1 file format. Never edit the fixture: if this breaks, add a migration.
    @Test
    void version1LibrariesStillLoad() throws Exception {
        Path libraryDirectory = tempDir.resolve("library");
        Files.createDirectories(libraryDirectory);
        try (var fixture = getClass().getResourceAsStream("/compat/lorebook_library_v1.json")) {
            Files.write(libraryDirectory.resolve("library.json"), fixture.readAllBytes());
        }
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(libraryDirectory);
        LocalLorebookBindingKey boundNpc = new LocalLorebookBindingKey(
                "singleplayer:/worlds/Test",
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        // v1 libraries could also hold character cards; those now live in the cards folder and are dropped.
        assertEquals(List.of("world.json"),
                store.listArtifacts().stream().map(ClientLorebookLibraryStore.ArtifactSummary::sourceFilename).toList());
        ClientLorebookContext context = store.activeContext(boundNpc);
        assertTrue(context.card().isEmpty());
        assertEquals(List.of("Dragons sleep under the mountain."), context.lorebooks().stream()
                .flatMap(book -> book.entries().stream())
                .map(ImportedLorebookEntry::content)
                .toList());
    }

    @Test
    void lorebookBoundToOneNpcIsActiveOnlyForThatNpc() throws Exception {
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID id = store.store(importJson("village.json", """
                {"entries":[{"id":"village","keys":["village"],"content":"Village lore."}]}
                """));
        LocalLorebookBindingKey miller = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());
        LocalLorebookBindingKey baker = new LocalLorebookBindingKey("singleplayer:example", miller.playerId(), UUID.randomUUID());

        store.bindLorebook(miller, id);

        ClientLorebookLibraryStore reloaded = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        assertEquals(1, reloaded.activeContext(miller).lorebooks().size());
        assertTrue(reloaded.activeContext(baker).lorebooks().isEmpty());
        assertTrue(reloaded.listArtifacts(Optional.of(miller)).getFirst().boundToCurrentNpc());
        assertFalse(reloaded.listArtifacts(Optional.of(baker)).getFirst().boundToCurrentNpc());

        reloaded.unbindLorebook(miller, id);
        assertTrue(reloaded.activeContext(miller).lorebooks().isEmpty());
    }

    @Test
    void lorebookThatIsBothGlobalAndNpcBoundIsIncludedOnce() throws Exception {
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID id = store.store(importJson("village.json", """
                {"entries":[{"id":"village","keys":["village"],"content":"Village lore."}]}
                """));
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());

        store.activateGlobal(id);
        store.bindLorebook(key, id);

        assertEquals(1, store.activeContext(key).lorebooks().size());
    }

    @Test
    void removingALorebookAlsoRemovesItsNpcBindings() throws Exception {
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID id = store.store(importJson("village.json", """
                {"entries":[{"id":"village","keys":["village"],"content":"Village lore."}]}
                """));
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());
        store.bindLorebook(key, id);

        store.remove(id);

        String saved = Files.readString(tempDir.resolve("library").resolve("library.json"));
        assertFalse(saved.contains(id.toString()));
    }

    @Test
    void unknownTopLevelFieldsSurviveASave() throws Exception {
        Path libraryDirectory = tempDir.resolve("library");
        Files.createDirectories(libraryDirectory);
        Files.writeString(libraryDirectory.resolve("library.json"),
                "{\"version\":1,\"artifacts\":[],\"globalActive\":[],\"bindings\":[],\"fromTheFuture\":{\"x\":1}}");
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(libraryDirectory);

        store.store(importJson("new.json", """
                {"entries":[{"id":"new","keys":["new"],"content":"New lore."}]}
                """));

        assertTrue(Files.readString(libraryDirectory.resolve("library.json")).contains("\"fromTheFuture\":{\"x\":1}"));
    }
}
