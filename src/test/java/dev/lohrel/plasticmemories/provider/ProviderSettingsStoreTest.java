package dev.lohrel.plasticmemories.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProviderSettingsStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsSettingsInAnOwnerOnlyFile() throws Exception {
        Path file = directory.resolve("provider.json");
        ProviderSettingsStore store = new ProviderSettingsStore(file);
        ProviderSettings expected = ProviderSettings.create(
                "https://example.com/v1/chat/completions", "example-model", "secret");

        store.save(expected);

        assertEquals(expected, store.load().orElseThrow());
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertEquals(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file));
        }
    }

    @Test
    void malformedSettingsFailClosed() throws Exception {
        Path file = directory.resolve("provider.json");
        Files.writeString(file, "not json");

        assertTrue(new ProviderSettingsStore(file).load().isEmpty());
    }
}
