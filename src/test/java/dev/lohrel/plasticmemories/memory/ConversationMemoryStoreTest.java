package dev.lohrel.plasticmemories.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConversationMemoryStoreTest {
    @TempDir
    Path directory;

    @Test
    void isolatesAndRoundTripsPrivateMemoryByWorldPlayerAndNpc() throws Exception {
        ConversationMemoryStore store = new ConversationMemoryStore(directory);
        UUID player = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        ConversationMemoryKey key = new ConversationMemoryKey("singleplayer:test-world", player, npc);
        ConversationMemory expected = ConversationMemory.empty().append("Hello", "Welcome back.");

        store.save(key, expected);

        assertEquals(expected, store.load(key));
        assertTrue(store.load(new ConversationMemoryKey("singleplayer:test-world", UUID.randomUUID(), npc))
                .turns()
                .isEmpty());
        Path storedFile = store.fileForTesting(key);
        if (Files.getFileStore(storedFile).supportsFileAttributeView("posix")) {
            assertEquals(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(storedFile));
        }
    }

    @Test
    void unreadableMemoryIsSetAsideInsteadOfBeingOverwritten() throws Exception {
        ConversationMemoryStore store = new ConversationMemoryStore(directory);
        ConversationMemoryKey key = new ConversationMemoryKey("singleplayer:test-world", UUID.randomUUID(), UUID.randomUUID());
        Path file = store.fileForTesting(key);
        Files.createDirectories(directory);
        Files.writeString(file, "{\"version\":99,\"turns\":[]}");

        assertTrue(store.load(key).turns().isEmpty());
        store.save(key, ConversationMemory.empty().append("Hi", "Hello."));

        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith(file.getFileName() + ".unreadable-")));
        }
    }

    @Test
    void memoryWithMoreTurnsThanTheCurrentLimitKeepsTheNewestTurns() throws Exception {
        ConversationMemoryStore store = new ConversationMemoryStore(directory);
        ConversationMemoryKey key = new ConversationMemoryKey("singleplayer:test-world", UUID.randomUUID(), UUID.randomUUID());
        StringBuilder turns = new StringBuilder();
        for (int turn = 1; turn <= ConversationMemory.MAX_TURNS + 3; turn++) {
            if (turns.length() > 0) {
                turns.append(',');
            }
            turns.append("{\"player\":\"p").append(turn).append("\",\"npc\":\"n").append(turn).append("\"}");
        }
        Files.createDirectories(directory);
        Files.writeString(store.fileForTesting(key), "{\"version\":1,\"turns\":[" + turns + "]}");

        ConversationMemory loaded = store.load(key);

        assertEquals(ConversationMemory.MAX_TURNS, loaded.turns().size());
        assertEquals("p" + (ConversationMemory.MAX_TURNS + 3), loaded.turns().getLast().playerMessage());
    }

    // Frozen copy of the v1 file format. Never edit the fixture: if this breaks, add a migration.
    @Test
    void version1FilesStillLoad() throws Exception {
        ConversationMemoryStore store = new ConversationMemoryStore(directory);
        ConversationMemoryKey key = new ConversationMemoryKey("singleplayer:test-world", UUID.randomUUID(), UUID.randomUUID());
        Files.createDirectories(directory);
        try (var fixture = getClass().getResourceAsStream("/compat/conversation_memory_v1.json")) {
            Files.write(store.fileForTesting(key), fixture.readAllBytes());
        }

        assertEquals(
                ConversationMemory.empty()
                        .append("Hello there.", "Welcome back, traveler.")
                        .append("Any news?", "The miller lost his cat again."),
                store.load(key));
    }
}
