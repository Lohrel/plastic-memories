package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookLibraryStoreManagementTest {
    @TempDir
    Path tempDir;

    @Test
    void listsContentFreeActivationStateAndRemovesAnArtifactFromFutureContext() throws Exception {
        ClientLorebookLibraryStore store = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID artifactId = store.store(LorebookImportResult.accepted(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                "forest.json",
                book(),
                0,
                List.of(),
                true));
        store.activateGlobal(artifactId);

        List<ClientLorebookLibraryStore.ArtifactSummary> artifacts = store.listArtifacts();

        assertEquals(1, artifacts.size());
        assertEquals(artifactId, artifacts.getFirst().id());
        assertEquals("forest.json", artifacts.getFirst().sourceFilename());
        assertTrue(artifacts.getFirst().globallyActive());
        assertTrue(store.remove(artifactId));
        assertTrue(store.listArtifacts().isEmpty());
        assertTrue(store.activeContext(new LocalLorebookBindingKey(
                        "singleplayer:example", UUID.randomUUID(), UUID.randomUUID()))
                .lorebooks()
                .isEmpty());
    }

    private static ImportedLorebook book() {
        ImportedLorebookEntry entry = new ImportedLorebookEntry(
                "forest",
                0,
                List.of("forest"),
                List.of(),
                "Forest content",
                0,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                LorebookActivationState.DEFAULT,
                LorebookRecursionOptions.DEFAULT);
        return new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
    }
}
