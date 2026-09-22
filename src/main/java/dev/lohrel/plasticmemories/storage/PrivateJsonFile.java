package dev.lohrel.plasticmemories.storage;

import com.google.gson.JsonElement;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Owner-only JSON files for the client stores (memory, provider settings, lorebook library).
 *
 * <p>A file that exists but can't be read (corrupt, too big, or a version this build doesn't know)
 * is renamed to {@code <name>.unreadable-<timestamp>} instead of being treated as empty, so the
 * next save can't overwrite the player's data. Downgrading the mod, or a bug in a newer format,
 * therefore never destroys anything.
 */
public final class PrivateJsonFile {
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private PrivateJsonFile() {
    }

    /**
     * Returns empty if the file is missing. If {@code parser} throws, or the file is unreadable,
     * the file is set aside and empty is returned.
     */
    public static <T> Optional<T> read(Path file, long maxBytes, Function<JsonObject, T> parser) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            if (Files.size(file) > maxBytes) {
                throw new IOException("File exceeds its size limit.");
            }
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return Optional.of(parser.apply(parsed.getAsJsonObject()));
        } catch (IOException | RuntimeException exception) {
            setAside(file);
            return Optional.empty();
        }
    }

    /** Throws unless the file declares exactly this version. Older versions need a migration before this check. */
    public static void requireVersion(JsonObject json, int expected) {
        JsonElement version = json.get("version");
        if (version == null || !version.isJsonPrimitive() || version.getAsInt() != expected) {
            throw new IllegalStateException("Unsupported file version.");
        }
    }

    /** Writes via a temp file and an atomic move, so a crash leaves either the old file or the new one. */
    public static void write(Path file, String json) throws IOException {
        write(file, json.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path file, byte[] content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(
                temporary,
                content,
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

    /** Creates the directory (if needed) and makes it owner-only. */
    public static void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        restrict(directory, OWNER_DIRECTORY);
    }

    private static void setAside(Path file) {
        Path target = file.resolveSibling(file.getFileName() + ".unreadable-" + System.currentTimeMillis());
        try {
            Files.move(file, target);
        } catch (IOException ignored) {
            // Nothing more we can do; the file stays where it is.
        }
    }

    private static void restrict(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }
}
