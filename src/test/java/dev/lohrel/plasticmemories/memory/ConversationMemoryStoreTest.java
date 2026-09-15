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
}
