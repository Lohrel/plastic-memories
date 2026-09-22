package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CharxCharacterCardImportTest {
    @TempDir
    Path tempDir;

    @Test
    void importsOnlyTheRootCardJsonFromAContentDetectedCharxArchive() throws Exception {
        Path source = tempDir.resolve("unrelated.dat");
        writeZip(source, "card.json", """
                {"spec":"chara_card_v3","data":{"name":"Archive Warden"}}
                """);

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertEquals(LorebookSourceFormat.CHARX_CHARACTER_CARD, result.format());
        assertEquals("Archive Warden", result.characterCard().orElseThrow().name());
        assertTrue(result.activationPossible());
    }

    @Test
    void rejectsArchivePathTraversalWithoutExposingItsContent() throws Exception {
        Path source = tempDir.resolve("traversal.charx");
        writeZip(source, "../card.json", "{}");

        LorebookImportResult result = LorebookImporter.importArtifact(source);

        assertFalse(result.activationPossible());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == LorebookImportDiagnosticCode.MALFORMED_CONTAINER));
    }

    private static void writeZip(Path path, String entryName, String content) throws Exception {
        try (OutputStream raw = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
