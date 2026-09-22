package dev.lohrel.plasticmemories.card;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lohrel.plasticmemories.lorebook.ImportedCharacterCard;
import dev.lohrel.plasticmemories.lorebook.LorebookArtifactDecoder;
import dev.lohrel.plasticmemories.lorebook.LorebookImportResult;
import dev.lohrel.plasticmemories.lorebook.LorebookImporter;
import dev.lohrel.plasticmemories.lorebook.LorebookLimits;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The character-cards folder is the card collection, like SillyTavern's characters folder: whatever
 * card files are in it (PNG, JSON, .charx) are the cards the player can pick. Nothing is copied.
 * Parsed cards are cached by file size and modification time.
 */
public final class CharacterCardFolder {
    private static final int MAX_FILES = 512;

    private final Path directory;
    private final Map<String, Cached> cache = new HashMap<>();

    public CharacterCardFolder(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public Path directory() {
        return directory;
    }

    /** Every readable card in the folder, sorted by character name. Other files are ignored. */
    public synchronized List<CardFile> list() {
        ArrayList<CardFile> cards = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return cards;
        }
        ArrayList<String> seen = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (seen.size() >= MAX_FILES) {
                    break;
                }
                String name = file.getFileName().toString();
                seen.add(name);
                read(name).ifPresent(cards::add);
            }
        } catch (IOException exception) {
            return cards;
        }
        cache.keySet().retainAll(seen);
        cards.sort(Comparator.comparing((CardFile card) -> card.card().name().toLowerCase(Locale.ROOT))
                .thenComparing(CardFile::fileName));
        return cards;
    }

    /** The card in {@code fileName}, if that file is directly in this folder and is a card. */
    public synchronized Optional<CardFile> find(String fileName) {
        return safeFile(fileName).isPresent() ? read(fileName) : Optional.empty();
    }

    /** Image bytes for the picker: the PNG itself, or the main icon inside a .charx. JSON cards have none. */
    public synchronized Optional<byte[]> readPortrait(String fileName) {
        Optional<Path> file = safeFile(fileName);
        Optional<CardFile> card = find(fileName);
        if (file.isEmpty() || card.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file.orElseThrow());
            if (LorebookArtifactDecoder.isPng(bytes)) {
                return Optional.of(bytes);
            }
            if (LorebookArtifactDecoder.isZip(bytes)) {
                return iconPath(card.orElseThrow().card())
                        .flatMap(path -> LorebookArtifactDecoder.readArchiveEntry(bytes, path));
            }
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<CardFile> read(String fileName) {
        Optional<Path> file = safeFile(fileName);
        if (file.isEmpty() || !Files.isRegularFile(file.orElseThrow(), LinkOption.NOFOLLOW_LINKS)) {
            cache.remove(fileName);
            return Optional.empty();
        }
        try {
            long size = Files.size(file.orElseThrow());
            long modified = Files.getLastModifiedTime(file.orElseThrow()).toMillis();
            Cached cached = cache.get(fileName);
            if (cached == null || cached.size() != size || cached.modified() != modified) {
                Optional<ImportedCharacterCard> card = size > LorebookLimits.MAX_RAW_INPUT_BYTES
                        ? Optional.empty()
                        : parse(file.orElseThrow());
                cached = new Cached(size, modified, card);
                cache.put(fileName, cached);
            }
            return cached.card().map(card -> new CardFile(fileName, card));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Optional<ImportedCharacterCard> parse(Path file) {
        LorebookImportResult result = LorebookImporter.importArtifact(file);
        return result.activationPossible() ? result.characterCard() : Optional.empty();
    }

    /** Rejects anything that isn't a plain file name, so a binding can't point outside the folder. */
    private Optional<Path> safeFile(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..")) {
            return Optional.empty();
        }
        Path file = directory.resolve(fileName).normalize();
        return directory.normalize().equals(file.getParent()) ? Optional.of(file) : Optional.empty();
    }

    /** V3 cards list their portrait as an "icon" asset, preferably named "main", at embeded://path. */
    private static Optional<String> iconPath(ImportedCharacterCard card) {
        String fallback = null;
        for (String descriptor : card.assetDescriptorsJson()) {
            try {
                JsonElement parsed = JsonParser.parseString(descriptor);
                if (!parsed.isJsonObject()) {
                    continue;
                }
                JsonObject asset = parsed.getAsJsonObject();
                if (!asset.has("type") || !"icon".equals(asset.get("type").getAsString()) || !asset.has("uri")) {
                    continue;
                }
                String uri = asset.get("uri").getAsString();
                // The V3 spec really spells it "embeded"; accept the correct spelling too.
                String path = uri.startsWith("embeded://") ? uri.substring("embeded://".length())
                        : uri.startsWith("embedded://") ? uri.substring("embedded://".length()) : null;
                if (path == null) {
                    continue;
                }
                if (asset.has("name") && "main".equals(asset.get("name").getAsString())) {
                    return Optional.of(path);
                }
                if (fallback == null) {
                    fallback = path;
                }
            } catch (RuntimeException ignored) {
                // Malformed descriptor: try the next one.
            }
        }
        return Optional.ofNullable(fallback);
    }

    public record CardFile(String fileName, ImportedCharacterCard card) {
    }

    private record Cached(long size, long modified, Optional<ImportedCharacterCard> card) {
    }
}
