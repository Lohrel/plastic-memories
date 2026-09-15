package dev.lohrel.plasticmemories.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Set;

public final class ConversationMemoryStore {
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 262_144;
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path directory;

    public ConversationMemoryStore(Path directory) {
        this.directory = directory;
    }

    public synchronized ConversationMemory load(ConversationMemoryKey key) {
        Path file = fileForTesting(key);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                return ConversationMemory.empty();
            }
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("version").getAsInt() != FORMAT_VERSION) {
                return ConversationMemory.empty();
            }
            JsonArray storedTurns = json.getAsJsonArray("turns");
            if (storedTurns.size() > ConversationMemory.MAX_TURNS) {
                return ConversationMemory.empty();
            }
            ArrayList<ConversationTurn> turns = new ArrayList<>(storedTurns.size());
            for (var element : storedTurns) {
                JsonObject storedTurn = element.getAsJsonObject();
                turns.add(new ConversationTurn(
                        storedTurn.get("player").getAsString(),
                        storedTurn.get("npc").getAsString()));
            }
            return new ConversationMemory(turns);
        } catch (IOException | RuntimeException exception) {
            return ConversationMemory.empty();
        }
    }

    public synchronized void save(ConversationMemoryKey key, ConversationMemory memory) throws IOException {
        Files.createDirectories(directory);
        restrict(directory, OWNER_DIRECTORY);

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

        Path file = fileForTesting(key);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                json.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        restrict(temporary, OWNER_FILE);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        restrict(file, OWNER_FILE);
    }

    public synchronized void clear(ConversationMemoryKey key) throws IOException {
        Files.deleteIfExists(fileForTesting(key));
    }

    Path fileForTesting(ConversationMemoryKey key) {
        return directory.resolve(hashKey(key) + ".json");
    }

    private static String hashKey(ConversationMemoryKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = key.worldIdentity() + "\u0000" + key.playerId() + "\u0000" + key.npcId();
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    private static void restrict(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }
}
