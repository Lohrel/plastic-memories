package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookAutoImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void importsEachAcceptedInboxFileOnceAndLeavesItInactive() throws Exception {
        Path inboxDirectory = tempDir.resolve("inbox");
        Files.createDirectories(inboxDirectory);
        Files.writeString(inboxDirectory.resolve("forest.json"), """
                {
                  "entries": [{
                    "id": "forest",
                    "keys": ["forest"],
                    "content": "The forest is ancient."
                  }]
                }
                """);
        ClientLorebookInbox inbox = new ClientLorebookInbox(inboxDirectory);
        ClientLorebookLibraryStore library = new ClientLorebookLibraryStore(tempDir.resolve("library"));

        ClientLorebookAutoImporter.ImportReport first = ClientLorebookAutoImporter.scanAndImport(inbox, library);
        ClientLorebookAutoImporter.ImportReport second = ClientLorebookAutoImporter.scanAndImport(inbox, library);

        assertEquals(1, first.importedCount());
        assertEquals(0, first.alreadyKnownCount());
        assertEquals(0, first.rejectedCount());
        assertEquals(0, first.failedCount());
        assertEquals(0, second.importedCount());
        assertEquals(1, second.alreadyKnownCount());
        assertEquals(1, library.listArtifacts().size());
        assertEquals(CompatibilityProfile.SILLY_TAVERN, library.listArtifacts().getFirst().profile());
        assertFalse(library.listArtifacts().getFirst().globallyActive());
    }
}
