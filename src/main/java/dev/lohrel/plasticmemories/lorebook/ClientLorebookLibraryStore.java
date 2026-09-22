package dev.lohrel.plasticmemories.lorebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lohrel.plasticmemories.storage.PrivateJsonFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The local library (library.json): imported items, which ones are switched on, and which card is bound to which NPC. */
public final class ClientLorebookLibraryStore {
    private static final int FORMAT_VERSION = 1;
    private static final String STATE_FILE = "library.json";
    // Fields a newer build adds are kept on save, so downgrading never strips them.
    private static final Set<String> KNOWN_TOP_LEVEL_FIELDS =
            Set.of("version", "artifacts", "globalActive", "bindings", "npcLorebooks");

    private final Path directory;

    public ClientLorebookLibraryStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** Adds an import to the library, switched off. */
    public synchronized UUID store(LorebookImportResult result) throws IOException {
        requireContent(result);
        return add(loadState(), result);
    }

    /** Like {@link #store}, but skips files whose name is already in the library. */
    public synchronized Optional<UUID> storeIfAbsent(LorebookImportResult result) throws IOException {
        requireContent(result);
        LibraryState state = loadState();
        if (state.artifacts().stream()
                .anyMatch(artifact -> artifact.result().sourceFilename().equals(result.sourceFilename()))) {
            return Optional.empty();
        }
        return Optional.of(add(state, result));
    }

    private static void requireContent(LorebookImportResult result) {
        Objects.requireNonNull(result, "result");
        if (result.characterCard().isEmpty() && result.lorebook().isEmpty()) {
            throw new IllegalArgumentException("An import without normalized content cannot be stored.");
        }
    }

    private UUID add(LibraryState state, LorebookImportResult result) throws IOException {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (state.find(id).isPresent());
        ArrayList<StoredArtifact> artifacts = new ArrayList<>(state.artifacts());
        artifacts.add(new StoredArtifact(id, result));
        saveState(new LibraryState(artifacts, state.globalActive(), state.bindings(), state.npcLorebooks(), state.extras()));
        return id;
    }

    /** Names and status only, for the library screen. */
    public synchronized List<ArtifactSummary> listArtifacts() {
        return listArtifacts(Optional.empty());
    }

    /** Same, plus whether each lorebook is switched on for {@code currentNpc}. */
    public synchronized List<ArtifactSummary> listArtifacts(Optional<LocalLorebookBindingKey> currentNpc) {
        LibraryState state = loadState();
        ArrayList<ArtifactSummary> summaries = new ArrayList<>();
        for (StoredArtifact artifact : state.artifacts()) {
            LorebookImportResult result = artifact.result();
            boolean bound = state.bindings().stream()
                    .anyMatch(binding -> binding.artifactId().equals(artifact.id()));
            summaries.add(new ArtifactSummary(
                    artifact.id(),
                    result.sourceFilename(),
                    result.format(),
                    result.profile(),
                    result.characterCard().isPresent(),
                    result.activationPossible(),
                    state.globalActive().contains(artifact.id()),
                    bound,
                    currentNpc.map(key -> state.npcLorebooks().contains(new CardBinding(key, artifact.id())))
                            .orElse(false),
                    result.skippedEntryCount()));
        }
        return List.copyOf(summaries);
    }

    /** Deletes an item and anything that referenced it (activation, card bindings). */
    public synchronized boolean remove(UUID id) throws IOException {
        Objects.requireNonNull(id, "id");
        LibraryState state = loadState();
        ArrayList<StoredArtifact> artifacts = new ArrayList<>();
        boolean removed = false;
        for (StoredArtifact artifact : state.artifacts()) {
            if (artifact.id().equals(id)) {
                removed = true;
            } else {
                artifacts.add(artifact);
            }
        }
        if (!removed) {
            return false;
        }
        LinkedHashSet<UUID> active = new LinkedHashSet<>(state.globalActive());
        active.remove(id);
        ArrayList<CardBinding> bindings = new ArrayList<>();
        for (CardBinding binding : state.bindings()) {
            if (!binding.artifactId().equals(id)) {
                bindings.add(binding);
            }
        }
        List<CardBinding> npcLorebooks = state.npcLorebooks().stream()
                .filter(binding -> !binding.artifactId().equals(id))
                .toList();
        saveState(new LibraryState(artifacts, active, bindings, npcLorebooks, state.extras()));
        return true;
    }

    /** Switches a lorebook on for every conversation. Cards must be bound instead. */
    public synchronized void activateGlobal(UUID id) throws IOException {
        LibraryState state = loadState();
        StoredArtifact artifact = state.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown imported artifact."));
        if (!artifact.result().activationPossible() || artifact.result().characterCard().isPresent()) {
            throw new IllegalArgumentException("Artifact cannot be globally activated.");
        }
        LinkedHashSet<UUID> active = new LinkedHashSet<>(state.globalActive());
        active.add(id);
        saveState(new LibraryState(state.artifacts(), active, state.bindings(), state.npcLorebooks(), state.extras()));
    }

    public synchronized void deactivateGlobal(UUID id) throws IOException {
        LibraryState state = loadState();
        LinkedHashSet<UUID> active = new LinkedHashSet<>(state.globalActive());
        active.remove(id);
        saveState(new LibraryState(state.artifacts(), active, state.bindings(), state.npcLorebooks(), state.extras()));
    }

    /** Binds this card to one NPC (in one world, for this player), replacing any card bound there before. */
    public synchronized void bindCard(LocalLorebookBindingKey key, UUID id) throws IOException {
        Objects.requireNonNull(key, "key");
        LibraryState state = loadState();
        StoredArtifact artifact = state.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown imported artifact."));
        if (!artifact.result().activationPossible() || artifact.result().characterCard().isEmpty()) {
            throw new IllegalArgumentException("Artifact cannot be used as a card binding.");
        }
        ArrayList<CardBinding> bindings = bindingsWithout(state, key);
        bindings.add(new CardBinding(key, id));
        saveState(new LibraryState(state.artifacts(), state.globalActive(), bindings, state.npcLorebooks(), state.extras()));
    }

    public synchronized void unbindCard(LocalLorebookBindingKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        LibraryState state = loadState();
        saveState(new LibraryState(state.artifacts(), state.globalActive(), bindingsWithout(state, key), state.npcLorebooks(), state.extras()));
    }

    private static ArrayList<CardBinding> bindingsWithout(LibraryState state, LocalLorebookBindingKey key) {
        ArrayList<CardBinding> bindings = new ArrayList<>();
        for (CardBinding binding : state.bindings()) {
            if (!binding.key().equals(key)) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    /** Switches a lorebook on for one NPC only (in one world, for this player). Cards use {@link #bindCard}. */
    public synchronized void bindLorebook(LocalLorebookBindingKey key, UUID id) throws IOException {
        Objects.requireNonNull(key, "key");
        LibraryState state = loadState();
        StoredArtifact artifact = state.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown imported artifact."));
        if (!artifact.result().activationPossible() || artifact.result().lorebook().isEmpty()) {
            throw new IllegalArgumentException("Artifact cannot be bound as a lorebook.");
        }
        CardBinding binding = new CardBinding(key, id);
        if (state.npcLorebooks().contains(binding)) {
            return;
        }
        ArrayList<CardBinding> npcLorebooks = new ArrayList<>(state.npcLorebooks());
        npcLorebooks.add(binding);
        saveState(new LibraryState(state.artifacts(), state.globalActive(), state.bindings(), npcLorebooks, state.extras()));
    }

    public synchronized void unbindLorebook(LocalLorebookBindingKey key, UUID id) throws IOException {
        Objects.requireNonNull(key, "key");
        LibraryState state = loadState();
        List<CardBinding> npcLorebooks = state.npcLorebooks().stream()
                .filter(binding -> !binding.equals(new CardBinding(key, id)))
                .toList();
        saveState(new LibraryState(state.artifacts(), state.globalActive(), state.bindings(), npcLorebooks, state.extras()));
    }

    /** What's switched on for this conversation. The bound card's own lorebook comes before global ones. */
    public synchronized ClientLorebookContext activeContext(LocalLorebookBindingKey key) {
        Objects.requireNonNull(key, "key");
        LibraryState state = loadState();
        Optional<ImportedCharacterCard> card = Optional.empty();
        ArrayList<ImportedLorebook> lorebooks = new ArrayList<>();
        for (CardBinding binding : state.bindings()) {
            if (!binding.key().equals(key)) {
                continue;
            }
            Optional<ImportedCharacterCard> boundCard = state.find(binding.artifactId())
                    .map(StoredArtifact::result)
                    .filter(LorebookImportResult::activationPossible)
                    .flatMap(LorebookImportResult::characterCard);
            if (boundCard.isPresent()) {
                ImportedCharacterCard importedCard = boundCard.orElseThrow();
                card = Optional.of(importedCard);
                importedCard.embeddedLorebook().ifPresent(lorebooks::add);
            }
            break;
        }
        LinkedHashSet<UUID> lorebookIds = new LinkedHashSet<>();
        state.npcLorebooks().stream()
                .filter(binding -> binding.key().equals(key))
                .forEach(binding -> lorebookIds.add(binding.artifactId()));
        lorebookIds.addAll(state.globalActive());
        for (UUID id : lorebookIds) {
            state.find(id)
                    .map(StoredArtifact::result)
                    .filter(LorebookImportResult::activationPossible)
                    .filter(result -> result.characterCard().isEmpty())
                    .flatMap(LorebookImportResult::lorebook)
                    .ifPresent(lorebooks::add);
        }
        return new ClientLorebookContext(card, lorebooks);
    }

    private LibraryState loadState() {
        return PrivateJsonFile.read(stateFile(), LorebookLimits.MAX_NORMALIZED_DOCUMENT_BYTES, ClientLorebookLibraryStore::parseState)
                .orElse(LibraryState.empty());
    }

    private static LibraryState parseState(JsonObject root) {
        PrivateJsonFile.requireVersion(root, FORMAT_VERSION);
        ArrayList<StoredArtifact> artifacts = new ArrayList<>();
        for (JsonElement artifact : array(root, "artifacts")) {
            artifacts.add(readArtifact(artifact.getAsJsonObject()));
        }
        LinkedHashSet<UUID> active = new LinkedHashSet<>();
        for (JsonElement id : array(root, "globalActive")) {
            active.add(UUID.fromString(id.getAsString()));
        }
        ArrayList<CardBinding> bindings = new ArrayList<>();
        for (JsonElement binding : array(root, "bindings")) {
            bindings.add(readBinding(binding.getAsJsonObject()));
        }
        // Added after v1 shipped; optional so v1 files without it still load.
        ArrayList<CardBinding> npcLorebooks = new ArrayList<>();
        if (root.has("npcLorebooks")) {
            for (JsonElement binding : array(root, "npcLorebooks")) {
                npcLorebooks.add(readBinding(binding.getAsJsonObject()));
            }
        }
        JsonObject extras = new JsonObject();
        for (String key : root.keySet()) {
            if (!KNOWN_TOP_LEVEL_FIELDS.contains(key)) {
                extras.add(key, root.get(key));
            }
        }
        return new LibraryState(artifacts, active, bindings, npcLorebooks, extras);
    }

    private void saveState(LibraryState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonArray artifacts = new JsonArray();
        for (StoredArtifact artifact : state.artifacts()) {
            artifacts.add(writeArtifact(artifact));
        }
        root.add("artifacts", artifacts);
        JsonArray active = new JsonArray();
        for (UUID id : state.globalActive()) {
            active.add(id.toString());
        }
        root.add("globalActive", active);
        JsonArray bindings = new JsonArray();
        for (CardBinding binding : state.bindings()) {
            bindings.add(writeBinding(binding));
        }
        root.add("bindings", bindings);
        JsonArray npcLorebooks = new JsonArray();
        for (CardBinding binding : state.npcLorebooks()) {
            npcLorebooks.add(writeBinding(binding));
        }
        root.add("npcLorebooks", npcLorebooks);
        state.extras().entrySet().forEach(extra -> root.add(extra.getKey(), extra.getValue()));
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LorebookLimits.MAX_NORMALIZED_DOCUMENT_BYTES) {
            throw new IOException("Client lorebook library exceeds its bounded storage budget.");
        }
        PrivateJsonFile.createPrivateDirectory(directory);
        PrivateJsonFile.write(stateFile(), bytes);
    }

    private static JsonObject writeArtifact(StoredArtifact artifact) {
        JsonObject json = new JsonObject();
        json.addProperty("id", artifact.id().toString());
        writeResult(json, artifact.result());
        return json;
    }

    private static StoredArtifact readArtifact(JsonObject json) {
        return new StoredArtifact(UUID.fromString(string(json, "id")), readResult(json));
    }

    private static void writeResult(JsonObject target, LorebookImportResult result) {
        target.addProperty("format", result.format().name());
        target.addProperty("profile", result.profile().name());
        target.addProperty("sourceFilename", result.sourceFilename());
        target.addProperty("acceptedEntryCount", result.acceptedEntryCount());
        target.addProperty("rejectedEntryCount", result.rejectedEntryCount());
        target.addProperty("activationPossible", result.activationPossible());
        JsonArray diagnostics = new JsonArray();
        for (LorebookImportDiagnostic diagnostic : result.diagnostics()) {
            JsonObject value = new JsonObject();
            value.addProperty("code", diagnostic.code().name());
            value.addProperty("severity", diagnostic.severity().name());
            diagnostics.add(value);
        }
        target.add("diagnostics", diagnostics);
        result.characterCard().ifPresent(card -> target.add("card", writeCard(card)));
        result.lorebook().ifPresent(lorebook -> target.add("lorebook", writeLorebook(lorebook)));
    }

    private static LorebookImportResult readResult(JsonObject json) {
        ArrayList<LorebookImportDiagnostic> diagnostics = new ArrayList<>();
        for (JsonElement value : array(json, "diagnostics")) {
            JsonObject diagnostic = value.getAsJsonObject();
            diagnostics.add(new LorebookImportDiagnostic(
                    LorebookImportDiagnosticCode.valueOf(string(diagnostic, "code")),
                    LorebookImportDiagnosticSeverity.valueOf(string(diagnostic, "severity"))));
        }
        Optional<ImportedCharacterCard> card = json.has("card")
                ? Optional.of(readCard(object(json, "card")))
                : Optional.empty();
        Optional<ImportedLorebook> lorebook = json.has("lorebook")
                ? Optional.of(readLorebook(object(json, "lorebook")))
                : Optional.empty();
        return new LorebookImportResult(
                LorebookSourceFormat.valueOf(string(json, "format")),
                CompatibilityProfile.fromPersistedName(string(json, "profile")),
                string(json, "sourceFilename"),
                card,
                lorebook,
                integer(json, "acceptedEntryCount"),
                integer(json, "rejectedEntryCount"),
                diagnostics,
                // Recomputed rather than read: older builds blocked whole files for problems that now only skip entries.
                card.isPresent() || lorebook.isPresent());
    }

    private static JsonObject writeLorebook(ImportedLorebook lorebook) {
        JsonObject json = new JsonObject();
        json.addProperty("format", lorebook.format().name());
        json.addProperty("profile", lorebook.profile().name());
        json.addProperty("sourceVersion", lorebook.sourceVersion());
        json.addProperty("defaultScanDepth", lorebook.defaultScanDepth());
        json.addProperty("recursiveScanning", lorebook.recursiveScanning());
        json.addProperty("maxRecursionSteps", lorebook.maxRecursionSteps());
        JsonArray entries = new JsonArray();
        for (ImportedLorebookEntry entry : lorebook.entries()) {
            entries.add(writeEntry(entry));
        }
        json.add("entries", entries);
        return json;
    }

    private static ImportedLorebook readLorebook(JsonObject json) {
        ArrayList<ImportedLorebookEntry> entries = new ArrayList<>();
        for (JsonElement entry : array(json, "entries")) {
            entries.add(readEntry(entry.getAsJsonObject()));
        }
        return new ImportedLorebook(
                LorebookSourceFormat.valueOf(string(json, "format")),
                CompatibilityProfile.fromPersistedName(string(json, "profile")),
                integer(json, "sourceVersion"),
                integer(json, "defaultScanDepth"),
                bool(json, "recursiveScanning"),
                integer(json, "maxRecursionSteps", 0),
                entries);
    }

    private static JsonObject writeEntry(ImportedLorebookEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entry.id());
        json.addProperty("sourceOrder", entry.sourceOrder());
        json.add("primaryKeys", strings(entry.primaryKeys()));
        json.add("secondaryKeys", strings(entry.secondaryKeys()));
        json.addProperty("content", entry.content());
        json.addProperty("order", entry.order());
        json.addProperty("enabled", entry.enabled());
        json.addProperty("constant", entry.constant());
        json.addProperty("selective", entry.selective());
        json.addProperty("secondaryKeyLogic", entry.secondaryKeyLogic().name());
        JsonObject match = new JsonObject();
        match.addProperty("caseSensitive", entry.matchOptions().caseSensitive());
        match.addProperty("wholeWord", entry.matchOptions().wholeWord());
        match.addProperty("regex", entry.matchOptions().regex());
        match.addProperty("scanDepth", entry.matchOptions().scanDepth());
        json.add("matchOptions", match);
        JsonObject insertion = new JsonObject();
        insertion.addProperty("sourcePosition", entry.insertion().sourcePosition());
        insertion.addProperty("depth", entry.insertion().depth());
        insertion.addProperty("role", entry.insertion().role().name());
        insertion.addProperty("outletName", entry.insertion().outletName());
        json.add("insertion", insertion);
        JsonObject activation = new JsonObject();
        activation.addProperty("probabilityPercent", entry.activationState().probabilityPercent());
        activation.addProperty("stickyTurns", entry.activationState().stickyTurns());
        activation.addProperty("cooldownTurns", entry.activationState().cooldownTurns());
        activation.addProperty("delayTurns", entry.activationState().delayTurns());
        activation.addProperty("group", entry.activationState().group());
        activation.addProperty("groupWeight", entry.activationState().groupWeight());
        json.add("activationState", activation);
        JsonObject recursion = new JsonObject();
        recursion.addProperty("recursive", entry.recursionOptions().recursive());
        recursion.addProperty("preventRecursion", entry.recursionOptions().preventRecursion());
        recursion.addProperty("excludeRecursion", entry.recursionOptions().excludeRecursion());
        recursion.addProperty("delayUntilRecursion", entry.recursionOptions().delayUntilRecursion());
        json.add("recursionOptions", recursion);
        return json;
    }

    private static ImportedLorebookEntry readEntry(JsonObject json) {
        JsonObject match = object(json, "matchOptions");
        JsonObject insertion = object(json, "insertion");
        JsonObject activation = object(json, "activationState");
        JsonObject recursion = object(json, "recursionOptions");
        return new ImportedLorebookEntry(
                string(json, "id"),
                integer(json, "sourceOrder"),
                strings(array(json, "primaryKeys")),
                strings(array(json, "secondaryKeys")),
                string(json, "content"),
                integer(json, "order"),
                bool(json, "enabled"),
                bool(json, "constant"),
                bool(json, "selective"),
                SecondaryKeyLogic.valueOf(string(json, "secondaryKeyLogic")),
                new LorebookMatchOptions(
                        bool(match, "caseSensitive"), bool(match, "wholeWord"), bool(match, "regex"), integer(match, "scanDepth")),
                new LorebookInsertion(
                        integer(insertion, "sourcePosition"),
                        integer(insertion, "depth"),
                        LorebookPromptRole.valueOf(string(insertion, "role")),
                        string(insertion, "outletName")),
                new LorebookActivationState(
                        integer(activation, "probabilityPercent"),
                        integer(activation, "stickyTurns"),
                        integer(activation, "cooldownTurns"),
                        integer(activation, "delayTurns"),
                        string(activation, "group"),
                        integer(activation, "groupWeight")),
                new LorebookRecursionOptions(
                        bool(recursion, "recursive"),
                        bool(recursion, "preventRecursion"),
                        bool(recursion, "excludeRecursion"), bool(recursion, "delayUntilRecursion")));
    }

    private static JsonObject writeCard(ImportedCharacterCard card) {
        JsonObject json = new JsonObject();
        json.addProperty("name", card.name());
        json.addProperty("description", card.description());
        json.addProperty("personality", card.personality());
        json.addProperty("scenario", card.scenario());
        json.addProperty("firstMessage", card.firstMessage());
        json.add("alternateGreetings", strings(card.alternateGreetings()));
        json.addProperty("exampleDialogue", card.exampleDialogue());
        json.addProperty("creator", card.creator());
        json.addProperty("creatorVersion", card.creatorVersion());
        json.add("tags", strings(card.tags()));
        json.addProperty("systemPrompt", card.systemPrompt());
        json.addProperty("postHistoryInstructions", card.postHistoryInstructions());
        json.addProperty("creatorNotes", card.creatorNotes());
        json.addProperty("nickname", card.nickname());
        json.add("sourceMetadataJson", strings(card.sourceMetadataJson()));
        json.add("assetDescriptorsJson", strings(card.assetDescriptorsJson()));
        json.addProperty("sourceExtensionsJson", card.sourceExtensionsJson());
        card.embeddedLorebook().ifPresent(lorebook -> json.add("embeddedLorebook", writeLorebook(lorebook)));
        return json;
    }

    private static ImportedCharacterCard readCard(JsonObject json) {
        Optional<ImportedLorebook> embeddedLorebook = json.has("embeddedLorebook")
                ? Optional.of(readLorebook(object(json, "embeddedLorebook")))
                : Optional.empty();
        return new ImportedCharacterCard(
                string(json, "name"),
                string(json, "description"),
                string(json, "personality"),
                string(json, "scenario"),
                string(json, "firstMessage"),
                strings(array(json, "alternateGreetings")),
                string(json, "exampleDialogue"),
                string(json, "creator"),
                string(json, "creatorVersion"),
                strings(array(json, "tags")),
                string(json, "systemPrompt"),
                string(json, "postHistoryInstructions"),
                string(json, "creatorNotes"),
                string(json, "nickname"),
                embeddedLorebook,
                strings(array(json, "sourceMetadataJson")),
                strings(array(json, "assetDescriptorsJson")),
                string(json, "sourceExtensionsJson"));
    }

    private static JsonObject writeBinding(CardBinding binding) {
        JsonObject json = new JsonObject();
        json.addProperty("worldIdentity", binding.key().worldIdentity());
        json.addProperty("playerId", binding.key().playerId().toString());
        json.addProperty("npcId", binding.key().npcId().toString());
        json.addProperty("artifactId", binding.artifactId().toString());
        return json;
    }

    private static CardBinding readBinding(JsonObject json) {
        return new CardBinding(
                new LocalLorebookBindingKey(
                        string(json, "worldIdentity"),
                        UUID.fromString(string(json, "playerId")),
                        UUID.fromString(string(json, "npcId"))),
                UUID.fromString(string(json, "artifactId")));
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static List<String> strings(JsonArray values) {
        ArrayList<String> result = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            result.add(value.getAsString());
        }
        return List.copyOf(result);
    }

    private static JsonArray array(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Invalid client lorebook library shape.");
        }
        return value.getAsJsonArray();
    }

    private static JsonObject object(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Invalid client lorebook library shape.");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Invalid client lorebook library shape.");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Invalid client lorebook library shape.");
        }
        return value.getAsInt();
    }

    private static int integer(JsonObject json, String name, int fallback) {
        JsonElement value = json.get(name);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Invalid client lorebook library shape.");
        }
        return value.getAsInt();
    }

    private static boolean bool(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Invalid client lorebook library shape.");
        }
        return value.getAsBoolean();
    }

    private Path stateFile() {
        return directory.resolve(STATE_FILE);
    }


    public record ArtifactSummary(
            UUID id,
            String sourceFilename,
            LorebookSourceFormat format,
            CompatibilityProfile profile,
            boolean characterCard,
            boolean activationPossible,
            boolean globallyActive,
            boolean boundToAnyCard,
            boolean boundToCurrentNpc,
            int skippedEntryCount) {
        public ArtifactSummary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceFilename, "sourceFilename");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(profile, "profile");
        }
    }

    private record StoredArtifact(UUID id, LorebookImportResult result) {
    }

    private record CardBinding(LocalLorebookBindingKey key, UUID artifactId) {
    }

    /**
     * @param bindings     card bound to each NPC (at most one per NPC)
     * @param npcLorebooks lorebooks switched on for single NPCs (any number per NPC)
     * @param extras       top-level fields this build doesn't know, written back untouched
     */
    private record LibraryState(
            List<StoredArtifact> artifacts,
            LinkedHashSet<UUID> globalActive,
            List<CardBinding> bindings,
            List<CardBinding> npcLorebooks,
            JsonObject extras) {
        private LibraryState(
                List<StoredArtifact> artifacts,
                Set<UUID> globalActive,
                List<CardBinding> bindings,
                List<CardBinding> npcLorebooks,
                JsonObject extras) {
            this(List.copyOf(artifacts), new LinkedHashSet<>(globalActive), List.copyOf(bindings),
                    List.copyOf(npcLorebooks), extras.deepCopy());
        }

        static LibraryState empty() {
            return new LibraryState(List.of(), Set.of(), List.of(), List.of(), new JsonObject());
        }

        Optional<StoredArtifact> find(UUID id) {
            return artifacts.stream().filter(artifact -> artifact.id().equals(id)).findFirst();
        }
    }
}
