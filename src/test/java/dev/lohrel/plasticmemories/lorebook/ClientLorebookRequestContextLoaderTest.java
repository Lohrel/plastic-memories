package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookRequestContextLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesMatchedLoreOnlyAfterTheArtifactIsExplicitlyActivatedForThisClient() throws Exception {
        ImportedLorebookEntry entry = entry("forest");
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
        ClientLorebookLibraryStore library = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID artifactId = library.store(LorebookImportResult.accepted(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                "forest.json",
                book,
                0,
                List.of(),
                true));
        ClientLorebookRequestContextLoader loader = new ClientLorebookRequestContextLoader(library);
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());

        assertEquals(List.of(), loader.load(key, ConversationMemory.empty(), "The forest is quiet.").loreEntries());

        library.activateGlobal(artifactId);

        assertEquals(List.of(entry), loader.load(key, ConversationMemory.empty(), "The forest is quiet.").loreEntries());
    }

    @Test
    void waitsForTheConfiguredPrivateMessageCountBeforeActivatingDelayedLore() throws Exception {
        ImportedLorebookEntry entry = entry(
                "lantern", "The lantern remains lit.", new LorebookActivationState(100, 0, 0, 2, "", 1));
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
        ClientLorebookLibraryStore library = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID artifactId = library.store(LorebookImportResult.accepted(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                "lantern.json",
                book,
                0,
                List.of(),
                true));
        library.activateGlobal(artifactId);
        ClientLorebookRequestContextLoader loader = new ClientLorebookRequestContextLoader(library);
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());

        assertEquals(List.of(), loader.load(key, ConversationMemory.empty(), "A lantern is here.").loreEntries());
        assertEquals(
                List.of(entry),
                loader.load(key, ConversationMemory.empty().append("A lantern is here.", "It glows."), "Still here.")
                        .loreEntries());
    }

    @Test
    void retainsTimedActivationOnlyInThePrivateLoaderForTheSameBinding() throws Exception {
        ImportedLorebookEntry entry = entry(
                "lantern", "The lantern remains lit.", new LorebookActivationState(100, 2, 0, 0, "", 1), 1);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
        ClientLorebookRequestContextLoader loader = activeLoader(book);
        LocalLorebookBindingKey key = new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID());

        assertEquals(List.of(entry), loader.load(key, ConversationMemory.empty(), "A lantern is here.").loreEntries());
        assertEquals(
                List.of(entry),
                loader.load(key, ConversationMemory.empty().append("A lantern is here.", "It glows."), "Unrelated.")
                        .loreEntries());
    }

    @Test
    void retainsRecentlyUsedScopedActivationStateWhenTheBoundedCacheEvictsAnOlderConversation() throws Exception {
        ImportedLorebookEntry entry = entry(
                "lantern", "The lantern remains lit.", new LorebookActivationState(100, 2, 0, 0, "", 1), 1);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
        ClientLorebookRequestContextLoader loader = activeLoader(book);
        ArrayList<LocalLorebookBindingKey> keys = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            LocalLorebookBindingKey key = new LocalLorebookBindingKey(
                    "singleplayer:example", UUID.randomUUID(), UUID.randomUUID());
            keys.add(key);
            loader.load(key, ConversationMemory.empty(), "A lantern is here.");
        }
        LocalLorebookBindingKey recentlyUsed = keys.getFirst();
        loader.load(recentlyUsed, ConversationMemory.empty(), "Unrelated.");

        loader.load(
                new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID()),
                ConversationMemory.empty(),
                "A lantern is here.");

        assertEquals(List.of(entry), loader.load(recentlyUsed, ConversationMemory.empty(), "Still unrelated.").loreEntries());
    }

    private ClientLorebookRequestContextLoader activeLoader(ImportedLorebook book) throws Exception {
        ClientLorebookLibraryStore library = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID artifactId = library.store(LorebookImportResult.accepted(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                "lorebook.json",
                book,
                0,
                List.of(),
                true));
        library.activateGlobal(artifactId);
        return new ClientLorebookRequestContextLoader(library);
    }

    private static ImportedLorebookEntry entry(
            String key, String content, LorebookActivationState activationState) {
        return entry(key, content, activationState, 3);
    }

    private static ImportedLorebookEntry entry(
            String key, String content, LorebookActivationState activationState, int scanDepth) {
        return new ImportedLorebookEntry(
                key,
                0,
                List.of(key),
                List.of(),
                content,
                0,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, scanDepth),
                LorebookInsertion.DEFAULT,
                activationState,
                LorebookRecursionOptions.DEFAULT);
    }

    private static ImportedLorebookEntry entry(String key) {
        return new ImportedLorebookEntry(
                key,
                0,
                List.of(key),
                List.of(),
                key + " content",
                0,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                LorebookActivationState.DEFAULT,
                LorebookRecursionOptions.DEFAULT);
    }
}
