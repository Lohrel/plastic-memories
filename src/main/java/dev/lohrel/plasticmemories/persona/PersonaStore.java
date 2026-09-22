package dev.lohrel.plasticmemories.persona;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import dev.lohrel.plasticmemories.storage.PrivateJsonFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * personas.json: the player's saved personas, which one is active, and personas locked to single
 * NPCs (a lock wins over the active one). Private to this client.
 */
public final class PersonaStore {
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 4_194_304;
    private static final int MAX_PERSONAS = 256;
    private static final Set<String> KNOWN_FIELDS = Set.of("version", "personas", "active", "npcLocks");

    private final Path file;

    public PersonaStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public synchronized List<Persona> list() {
        return List.copyOf(load().personas().values());
    }

    public synchronized Persona create(String name, String description) throws IOException {
        State state = load();
        if (state.personas().size() >= MAX_PERSONAS) {
            throw new IllegalArgumentException("Too many personas.");
        }
        Persona persona = new Persona(UUID.randomUUID(), name, description);
        state.personas().put(persona.id(), persona);
        save(state);
        return persona;
    }

    public synchronized void update(Persona persona) throws IOException {
        State state = load();
        if (!state.personas().containsKey(persona.id())) {
            throw new IllegalArgumentException("Unknown persona.");
        }
        state.personas().put(persona.id(), persona);
        save(state);
    }

    /** Also clears it as the active persona and removes its NPC locks. */
    public synchronized void delete(UUID id) throws IOException {
        State state = load();
        state.personas().remove(id);
        state.npcLocks().values().removeIf(id::equals);
        save(new State(state.personas(), id.equals(state.active()) ? null : state.active(), state.npcLocks(),
                state.extras(), state.unreadable()));
    }

    public synchronized Optional<Persona> active() {
        State state = load();
        return Optional.ofNullable(state.active()).map(state.personas()::get);
    }

    public synchronized void setActive(UUID id) throws IOException {
        State state = load();
        if (id != null && !state.personas().containsKey(id)) {
            throw new IllegalArgumentException("Unknown persona.");
        }
        save(new State(state.personas(), id, state.npcLocks(), state.extras(), state.unreadable()));
    }

    /** Makes the next saved persona active (wrapping around) and returns it; empty if none are saved. */
    public synchronized Optional<Persona> cycleActive() throws IOException {
        State state = load();
        List<UUID> ids = new ArrayList<>(state.personas().keySet());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        int next = (ids.indexOf(state.active()) + 1) % ids.size();
        save(new State(state.personas(), ids.get(next), state.npcLocks(), state.extras(), state.unreadable()));
        return Optional.of(state.personas().get(ids.get(next)));
    }

    public synchronized Optional<Persona> lockedFor(LocalLorebookBindingKey npc) {
        State state = load();
        return Optional.ofNullable(state.npcLocks().get(npc)).map(state.personas()::get);
    }

    public synchronized void lock(LocalLorebookBindingKey npc, UUID id) throws IOException {
        State state = load();
        if (!state.personas().containsKey(id)) {
            throw new IllegalArgumentException("Unknown persona.");
        }
        state.npcLocks().put(Objects.requireNonNull(npc, "npc"), id);
        save(state);
    }

    public synchronized void unlock(LocalLorebookBindingKey npc) throws IOException {
        State state = load();
        if (state.npcLocks().remove(npc) != null) {
            save(state);
        }
    }

    /** The persona to use with this NPC: its lock if it has one, otherwise the active persona. */
    public synchronized Optional<Persona> personaFor(LocalLorebookBindingKey npc) {
        return lockedFor(npc).or(this::active);
    }

    private State load() {
        return PrivateJsonFile.read(file, MAX_FILE_BYTES, PersonaStore::parse)
                .orElseGet(() -> new State(new LinkedHashMap<>(), null, new LinkedHashMap<>(), new JsonObject(), List.of()));
    }

    private static State parse(JsonObject json) {
        PrivateJsonFile.requireVersion(json, FORMAT_VERSION);
        LinkedHashMap<UUID, Persona> personas = new LinkedHashMap<>();
        List<JsonObject> unreadable = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("personas")) {
            JsonObject stored = element.getAsJsonObject();
            try {
                Persona persona = new Persona(
                        UUID.fromString(stored.get("id").getAsString()),
                        stored.get("name").getAsString(),
                        stored.get("description").getAsString());
                personas.put(persona.id(), persona);
            } catch (RuntimeException exception) {
                // Can't be used by this build (e.g. over a limit), but it's written back unchanged on save.
                unreadable.add(stored);
            }
        }
        UUID active = json.has("active") && !json.get("active").isJsonNull()
                ? UUID.fromString(json.get("active").getAsString())
                : null;
        LinkedHashMap<LocalLorebookBindingKey, UUID> locks = new LinkedHashMap<>();
        for (JsonElement element : json.getAsJsonArray("npcLocks")) {
            JsonObject lock = element.getAsJsonObject();
            locks.put(new LocalLorebookBindingKey(
                            lock.get("worldIdentity").getAsString(),
                            UUID.fromString(lock.get("playerId").getAsString()),
                            UUID.fromString(lock.get("npcId").getAsString())),
                    UUID.fromString(lock.get("persona").getAsString()));
        }
        JsonObject extras = new JsonObject();
        json.keySet().stream().filter(key -> !KNOWN_FIELDS.contains(key)).forEach(key -> extras.add(key, json.get(key)));
        return new State(personas, active, locks, extras, unreadable);
    }

    private void save(State state) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        JsonArray personas = new JsonArray();
        for (Persona persona : state.personas().values()) {
            JsonObject stored = new JsonObject();
            stored.addProperty("id", persona.id().toString());
            stored.addProperty("name", persona.name());
            stored.addProperty("description", persona.description());
            personas.add(stored);
        }
        state.unreadable().forEach(personas::add);
        json.add("personas", personas);
        if (state.active() != null) {
            json.addProperty("active", state.active().toString());
        }
        JsonArray locks = new JsonArray();
        state.npcLocks().forEach((npc, persona) -> {
            JsonObject lock = new JsonObject();
            lock.addProperty("worldIdentity", npc.worldIdentity());
            lock.addProperty("playerId", npc.playerId().toString());
            lock.addProperty("npcId", npc.npcId().toString());
            lock.addProperty("persona", persona.toString());
            locks.add(lock);
        });
        json.add("npcLocks", locks);
        // Fields from a newer build survive a save, so downgrading doesn't strip them.
        state.extras().entrySet().forEach(extra -> json.add(extra.getKey(), extra.getValue()));
        PrivateJsonFile.write(file, json.toString());
    }

    /** {@code unreadable}: stored personas this build can't use, kept so a save doesn't delete them. */
    private record State(
            Map<UUID, Persona> personas,
            UUID active,
            Map<LocalLorebookBindingKey, UUID> npcLocks,
            JsonObject extras,
            List<JsonObject> unreadable) {
    }
}
