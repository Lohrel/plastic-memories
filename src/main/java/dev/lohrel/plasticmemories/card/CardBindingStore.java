package dev.lohrel.plasticmemories.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import dev.lohrel.plasticmemories.storage.PrivateJsonFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * card-bindings.json: which card file each NPC uses, per world and player, plus the player's
 * in-game edits to that card. Private to this client; the card files themselves are never written.
 */
public final class CardBindingStore {
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 1_048_576;
    private static final Set<String> KNOWN_FIELDS = Set.of("version", "bindings");

    private final Path file;

    public CardBindingStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public synchronized Optional<String> boundFile(LocalLorebookBindingKey key) {
        return Optional.ofNullable(load().bindings().get(Objects.requireNonNull(key, "key"))).map(Binding::file);
    }

    /** Edited fields for this NPC's card; empty when nothing was changed or no card is bound. */
    public synchronized Map<CardField, String> edits(LocalLorebookBindingKey key) {
        Binding binding = load().bindings().get(Objects.requireNonNull(key, "key"));
        return binding == null ? Map.of() : Map.copyOf(binding.edits());
    }

    /** Picking a different card drops the edits, which belonged to the old one; picking the same card keeps them. */
    public synchronized void bind(LocalLorebookBindingKey key, String fileName) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        State state = load();
        Binding current = state.bindings().get(Objects.requireNonNull(key, "key"));
        if (current != null && current.file().equals(fileName)) {
            return;
        }
        state.bindings().put(key, new Binding(fileName, new EnumMap<>(CardField.class)));
        save(state);
    }

    /** Replaces all edits for this NPC's card. Does nothing if no card is bound. */
    public synchronized void saveEdits(LocalLorebookBindingKey key, Map<CardField, String> edits) throws IOException {
        State state = load();
        Binding current = state.bindings().get(Objects.requireNonNull(key, "key"));
        if (current == null) {
            return;
        }
        EnumMap<CardField, String> copy = new EnumMap<>(CardField.class);
        copy.putAll(edits);
        state.bindings().put(key, new Binding(current.file(), copy));
        save(state);
    }

    public synchronized void unbind(LocalLorebookBindingKey key) throws IOException {
        State state = load();
        if (state.bindings().remove(Objects.requireNonNull(key, "key")) != null) {
            save(state);
        }
    }

    private State load() {
        return PrivateJsonFile.read(file, MAX_FILE_BYTES, CardBindingStore::parse)
                .orElseGet(() -> new State(new LinkedHashMap<>(), new JsonObject()));
    }

    private static State parse(JsonObject json) {
        PrivateJsonFile.requireVersion(json, FORMAT_VERSION);
        LinkedHashMap<LocalLorebookBindingKey, Binding> bindings = new LinkedHashMap<>();
        for (JsonElement element : json.getAsJsonArray("bindings")) {
            JsonObject binding = element.getAsJsonObject();
            // "edits" is optional; fields this build doesn't know are ignored.
            EnumMap<CardField, String> edits = new EnumMap<>(CardField.class);
            if (binding.has("edits") && binding.get("edits").isJsonObject()) {
                JsonObject stored = binding.getAsJsonObject("edits");
                for (String name : stored.keySet()) {
                    CardField field = CardField.fromStoredName(name);
                    if (field != null) {
                        edits.put(field, stored.get(name).getAsString());
                    }
                }
            }
            bindings.put(new LocalLorebookBindingKey(
                            binding.get("worldIdentity").getAsString(),
                            UUID.fromString(binding.get("playerId").getAsString()),
                            UUID.fromString(binding.get("npcId").getAsString())),
                    new Binding(binding.get("file").getAsString(), edits));
        }
        JsonObject extras = new JsonObject();
        json.keySet().stream()
                .filter(key -> !KNOWN_FIELDS.contains(key))
                .forEach(key -> extras.add(key, json.get(key)));
        return new State(bindings, extras);
    }

    private void save(State state) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        JsonArray bindings = new JsonArray();
        state.bindings().forEach((key, value) -> {
            JsonObject binding = new JsonObject();
            binding.addProperty("worldIdentity", key.worldIdentity());
            binding.addProperty("playerId", key.playerId().toString());
            binding.addProperty("npcId", key.npcId().toString());
            binding.addProperty("file", value.file());
            JsonObject edits = new JsonObject();
            value.edits().forEach((field, text) -> edits.addProperty(field.storedName(), text));
            binding.add("edits", edits);
            bindings.add(binding);
        });
        json.add("bindings", bindings);
        // Fields from a newer build survive a save, so downgrading doesn't strip them.
        state.extras().entrySet().forEach(extra -> json.add(extra.getKey(), extra.getValue()));
        PrivateJsonFile.write(file, json.toString());
    }

    private record State(Map<LocalLorebookBindingKey, Binding> bindings, JsonObject extras) {
    }

    private record Binding(String file, Map<CardField, String> edits) {
    }
}
