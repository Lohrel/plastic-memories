package dev.lohrel.plasticmemories.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PrivateJsonFileTest {
    @TempDir
    Path directory;

    @Test
    void missingFileReadsAsEmptyWithoutCreatingAnything() throws Exception {
        Path file = directory.resolve("data.json");

        assertTrue(PrivateJsonFile.read(file, 1_024, json -> "parsed").isEmpty());
        assertEquals(List.of(), listFiles());
    }

    @Test
    void writeThenReadRoundTripsInAnOwnerOnlyFile() throws Exception {
        Path file = directory.resolve("nested").resolve("data.json");

        PrivateJsonFile.write(file, "{\"version\":1,\"value\":\"hello\"}");

        assertEquals(Optional.of("hello"), PrivateJsonFile.read(file, 1_024, json -> json.get("value").getAsString()));
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertEquals(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file));
        }
    }

    @Test
    void corruptFileIsSetAsideSoTheNextWriteCannotDestroyIt() throws Exception {
        Path file = directory.resolve("data.json");
        Files.writeString(file, "not json");

        assertTrue(PrivateJsonFile.read(file, 1_024, json -> "parsed").isEmpty());
        PrivateJsonFile.write(file, "{\"version\":1}");

        assertEquals("not json", onlySetAsideContent("data.json"));
    }

    @Test
    void fileTheParserRejectsIsSetAside() throws Exception {
        Path file = directory.resolve("data.json");
        Files.writeString(file, "{\"version\":99}");

        assertTrue(PrivateJsonFile.read(file, 1_024, json -> {
            PrivateJsonFile.requireVersion(json, 1);
            return "parsed";
        }).isEmpty());

        assertTrue(Files.notExists(file));
        assertEquals("{\"version\":99}", onlySetAsideContent("data.json"));
    }

    @Test
    void oversizedFileIsSetAside() throws Exception {
        Path file = directory.resolve("data.json");
        Files.writeString(file, "{\"version\":1,\"padding\":\"" + "x".repeat(2_000) + "\"}");

        assertTrue(PrivateJsonFile.read(file, 1_024, json -> "parsed").isEmpty());

        assertTrue(Files.notExists(file));
        assertTrue(onlySetAsideContent("data.json").startsWith("{\"version\":1"));
    }

    @Test
    void requireVersionRejectsOtherVersionsAndMissingVersion() {
        var future = com.google.gson.JsonParser.parseString("{\"version\":2}").getAsJsonObject();
        var missing = com.google.gson.JsonParser.parseString("{}").getAsJsonObject();

        PrivateJsonFile.requireVersion(com.google.gson.JsonParser.parseString("{\"version\":1}").getAsJsonObject(), 1);
        assertThrows(RuntimeException.class, () -> PrivateJsonFile.requireVersion(future, 1));
        assertThrows(RuntimeException.class, () -> PrivateJsonFile.requireVersion(missing, 1));
    }

    private String onlySetAsideContent(String originalName) throws Exception {
        List<Path> setAside = listFiles().stream()
                .filter(path -> path.getFileName().toString().startsWith(originalName + ".unreadable-"))
                .toList();
        assertEquals(1, setAside.size(), "expected exactly one set-aside copy");
        return Files.readString(setAside.getFirst(), StandardCharsets.UTF_8);
    }

    private List<Path> listFiles() throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }
}
