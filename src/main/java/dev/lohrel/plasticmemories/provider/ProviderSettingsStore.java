package dev.lohrel.plasticmemories.provider;

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

/** Reads and writes provider.json. The file is owner-only because it holds the API key. */
public final class ProviderSettingsStore {
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 16_384;
    private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path file;

    public ProviderSettingsStore(Path file) {
        this.file = file;
    }

    public Optional<ProviderSettings> load() {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                return Optional.empty();
            }
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("version").getAsInt() != FORMAT_VERSION) {
                return Optional.empty();
            }
            return Optional.of(ProviderSettings.create(
                    json.get("endpoint").getAsString(),
                    json.get("model").getAsString(),
                    json.get("apiKey").getAsString()));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public void save(ProviderSettings settings) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        json.addProperty("endpoint", settings.endpoint().toString());
        json.addProperty("model", settings.model());
        json.addProperty("apiKey", settings.apiKey());

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                json.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        restrictPermissions(temporary);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        restrictPermissions(file);
    }

    private static void restrictPermissions(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, OWNER_READ_WRITE);
        }
    }
}
