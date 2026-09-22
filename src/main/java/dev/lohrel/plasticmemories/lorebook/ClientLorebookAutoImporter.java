package dev.lohrel.plasticmemories.lorebook;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Imports new lorebook files from the inbox into the library, switched off. Character cards are counted, not imported. */
public final class ClientLorebookAutoImporter {
    private ClientLorebookAutoImporter() {
    }

    public static ImportReport scanAndImport(
            ClientLorebookInbox inbox,
            ClientLorebookLibraryStore library) {
        Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(library, "library");
        int imported = 0;
        int alreadyKnown = 0;
        int rejected = 0;
        int failed = 0;
        int characterCards = 0;
        for (Path source : inbox.files()) {
            LorebookImportResult result = LorebookImporter.importArtifact(source);
            if (result.characterCard().isPresent()) {
                // Cards go in the character-cards folder; the library screen tells the player so.
                characterCards++;
                continue;
            }
            if (!result.activationPossible()) {
                rejected++;
                continue;
            }
            try {
                if (library.storeIfAbsent(result).isPresent()) {
                    imported++;
                } else {
                    alreadyKnown++;
                }
            } catch (IOException | IllegalArgumentException exception) {
                failed++;
            }
        }
        return new ImportReport(imported, alreadyKnown, rejected, failed, characterCards);
    }

    public record ImportReport(
            int importedCount, int alreadyKnownCount, int rejectedCount, int failedCount, int characterCardCount) {
    }
}
