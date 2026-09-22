package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookInboxTest {
    @TempDir
    Path tempDir;

    @Test
    void listsOnlyDirectRegularFilesInStableFilenameOrder() throws Exception {
        Path inbox = tempDir.resolve("inbox");
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("zeta.json"), "{}");
        Files.writeString(inbox.resolve("alpha.card"), "{}");
        Files.createDirectory(inbox.resolve("nested"));

        List<Path> files = new ClientLorebookInbox(inbox).files();

        assertEquals(List.of(inbox.resolve("alpha.card"), inbox.resolve("zeta.json")), files);
    }
}
