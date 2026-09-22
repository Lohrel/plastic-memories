package dev.lohrel.plasticmemories.lorebook;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Imports new files from the inbox folder into the library. Imported items start switched off. */
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
        for (Path source : inbox.files()) {
            LorebookImportResult result = LorebookImporter.importArtifact(source);
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
        return new ImportReport(imported, alreadyKnown, rejected, failed);
    }

    public record ImportReport(int importedCount, int alreadyKnownCount, int rejectedCount, int failedCount) {
    }
}
