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

    @Test
    void unreadableSettingsAreSetAsideInsteadOfBeingOverwritten() throws Exception {
        Path file = directory.resolve("provider.json");
        Files.writeString(file, "{\"version\":99}");
        ProviderSettingsStore store = new ProviderSettingsStore(file);

        assertTrue(store.load().isEmpty());
        store.save(ProviderSettings.create("https://example.com/v1", "example-model", "secret"));

        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("provider.json.unreadable-")));
        }
    }

    // Frozen copy of the v1 file format. Never edit the fixture: if this breaks, add a migration.
    @Test
    void version1FilesStillLoad() throws Exception {
        Path file = directory.resolve("provider.json");
        try (var fixture = getClass().getResourceAsStream("/compat/provider_v1.json")) {
            Files.write(file, fixture.readAllBytes());
        }

        assertEquals(
                ProviderSettings.create("https://example.com/v1", "example-model", "fixture-key"),
                new ProviderSettingsStore(file).load().orElseThrow());
    }
}
