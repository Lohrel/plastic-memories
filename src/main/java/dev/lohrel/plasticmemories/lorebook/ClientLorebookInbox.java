package dev.lohrel.plasticmemories.lorebook;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded client-local inbox; imported artifacts are detected by content, never filename. */
public final class ClientLorebookInbox {
    private static final int MAX_FILES = 256;
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    private final Path directory;

    public ClientLorebookInbox(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public List<Path> files() {
        try {
            Files.createDirectories(directory);
            restrict(directory);
            ArrayList<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path candidate : stream) {
                    if (files.size() >= MAX_FILES) {
                        break;
                    }
                    if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                        files.add(candidate);
                    }
                }
            }
            files.sort(Comparator.comparing(path -> path.getFileName().toString()));
            return List.copyOf(files);
        } catch (IOException | SecurityException exception) {
            return List.of();
        }
    }

    public Path directory() {
        return directory;
    }

    private static void restrict(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, OWNER_DIRECTORY);
        }
    }
}
