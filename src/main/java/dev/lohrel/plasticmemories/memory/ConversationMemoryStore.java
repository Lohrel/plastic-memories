package dev.lohrel.plasticmemories.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lohrel.plasticmemories.storage.PrivateJsonFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Saves each conversation's memory as a JSON file under config/. A file that can't be read loads
 * as empty memory and is set aside rather than overwritten (see {@link PrivateJsonFile}).
 */
public final class ConversationMemoryStore {
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 262_144;

    private final Path directory;

    public ConversationMemoryStore(Path directory) {
        this.directory = directory;
    }

    public synchronized ConversationMemory load(ConversationMemoryKey key) {
        return PrivateJsonFile.read(fileForTesting(key), MAX_FILE_BYTES, ConversationMemoryStore::parse)
                .orElse(ConversationMemory.empty());
    }

    public synchronized void save(ConversationMemoryKey key, ConversationMemory memory) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        JsonArray turns = new JsonArray();
        for (ConversationTurn turn : memory.turns()) {
            JsonObject storedTurn = new JsonObject();
            storedTurn.addProperty("player", turn.playerMessage());
            storedTurn.addProperty("npc", turn.npcReply());
            turns.add(storedTurn);
        }
        json.add("turns", turns);

        PrivateJsonFile.createPrivateDirectory(directory);
        PrivateJsonFile.write(fileForTesting(key), json.toString());
    }

    public synchronized void clear(ConversationMemoryKey key) throws IOException {
        Files.deleteIfExists(fileForTesting(key));
    }

    Path fileForTesting(ConversationMemoryKey key) {
        return directory.resolve(hashKey(key) + ".json");
    }

    private static ConversationMemory parse(JsonObject json) {
        PrivateJsonFile.requireVersion(json, FORMAT_VERSION);
        // Replay through append() so a file saved under larger limits keeps its newest turns.
        ConversationMemory memory = ConversationMemory.empty();
        for (var element : json.getAsJsonArray("turns")) {
            JsonObject storedTurn = element.getAsJsonObject();
            memory = memory.append(storedTurn.get("player").getAsString(), storedTurn.get("npc").getAsString());
        }
        return memory;
    }

    /** Hashed so file names don't reveal server addresses or world paths. */
    private static String hashKey(ConversationMemoryKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = key.worldIdentity() + "\u0000" + key.playerId() + "\u0000" + key.npcId();
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }
}
