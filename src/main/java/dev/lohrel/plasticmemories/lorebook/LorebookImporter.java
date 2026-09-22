package dev.lohrel.plasticmemories.lorebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Converts externally shaped artifacts into normalized client-local models. */
public final class LorebookImporter {

    private LorebookImporter() {
    }

    /**
     * Reads a bounded JSON artifact, detects its shape from content, and
     * returns a content-safe preview/report. It never places source text in a
     * diagnostic or exception message.
     */
    public static LorebookImportResult importArtifact(Path file) {
        Objects.requireNonNull(file, "file");
        String sourceFilename = sourceFilename(file);
        byte[] input;
        try {
            input = readBoundedBytes(file);
        } catch (InputTooLargeException exception) {
            return LorebookImportResult.rejected(
                    LorebookSourceFormat.UNSUPPORTED,
                    CompatibilityProfile.SILLY_TAVERN,
                    sourceFilename,
                    LorebookImportDiagnosticCode.INPUT_TOO_LARGE);
        } catch (IOException exception) {
            return LorebookImportResult.rejected(
                    LorebookSourceFormat.UNSUPPORTED,
                    CompatibilityProfile.SILLY_TAVERN,
                    sourceFilename,
                    LorebookImportDiagnosticCode.INPUT_READ_FAILED);
        }

        LorebookArtifactDecoder.DecodedArtifact decoded;
        try {
            decoded = LorebookArtifactDecoder.decode(input);
        } catch (LorebookArtifactDecoder.ArtifactDecodingException exception) {
            return LorebookImportResult.rejected(
                    LorebookSourceFormat.UNSUPPORTED,
                    CompatibilityProfile.SILLY_TAVERN,
                    sourceFilename,
                    LorebookImportDiagnosticCode.MALFORMED_CONTAINER);
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(decoded.json());
        } catch (RuntimeException exception) {
            return LorebookImportResult.rejected(
                    LorebookSourceFormat.UNSUPPORTED,
                    CompatibilityProfile.SILLY_TAVERN,
                    sourceFilename,
                    LorebookImportDiagnosticCode.MALFORMED_JSON);
        }
        if (!root.isJsonObject()) {
            return LorebookImportResult.rejected(
                    LorebookSourceFormat.UNSUPPORTED,
                    CompatibilityProfile.SILLY_TAVERN,
                    sourceFilename,
                    LorebookImportDiagnosticCode.INVALID_ROOT);
        }

        JsonObject object = root.getAsJsonObject();
        LorebookSourceFormat detectedFormat = detectFormat(object);
        LorebookSourceFormat format = decoded.containerFormat().orElse(detectedFormat);
        boolean cardArtifact = isCharacterCard(detectedFormat)
                || detectedFormat == LorebookSourceFormat.MARINARA_CHARACTER;
        if (detectedFormat == LorebookSourceFormat.UNSUPPORTED
                || (!decoded.containerFormat().isEmpty() && !cardArtifact)) {
            return LorebookImportResult.rejected(
                    format,
                    CompatibilityProfile.forFormat(detectedFormat),
                    sourceFilename,
                    LorebookImportDiagnosticCode.UNSUPPORTED_FORMAT);
        }

        CompatibilityProfile profile = CompatibilityProfile.forFormat(detectedFormat);
        try {
            if (cardArtifact) {
                JsonObject cardData = cardData(object, format);
                JsonElement embeddedEntries = findEntries(object);
                ParsedImportedEntries parsed = analyzeUnsupportedFeatures(parseEntries(embeddedEntries, profile));
                Optional<ImportedLorebook> embeddedLorebook = embeddedEntries == null
                        ? Optional.empty()
                        : Optional.of(createImportedLorebook(object, format, profile, parsed.entries()));
                ImportedCharacterCard characterCard = parseCharacterCard(
                        cardData, embeddedLorebook, detectedFormat == LorebookSourceFormat.CHARACTER_CARD_V3);
                ArrayList<LorebookImportDiagnostic> diagnostics = new ArrayList<>(parsed.diagnostics());
                if (!characterCard.sourceExtensionsJson().isBlank()) {
                    diagnostics.add(warning(LorebookImportDiagnosticCode.UNKNOWN_SOURCE_EXTENSION));
                }
                boolean activationPossible = activationPossible(parsed);
                return LorebookImportResult.acceptedCard(
                        format,
                        profile,
                        sourceFilename,
                        characterCard,
                        embeddedLorebook,
                        parsed.rejectedEntryCount(),
                        List.copyOf(diagnostics),
                        activationPossible);
            }

            JsonElement entries = locateEntries(object);
            if (!entries.isJsonObject() && !entries.isJsonArray()) {
                return LorebookImportResult.rejected(
                        format, profile, sourceFilename, LorebookImportDiagnosticCode.INVALID_ROOT);
            }
            ParsedImportedEntries parsed = analyzeUnsupportedFeatures(parseEntries(entries, profile));
            ImportedLorebook lorebook = createImportedLorebook(object, format, profile, parsed.entries());
            boolean activationPossible = activationPossible(parsed);
            return LorebookImportResult.accepted(
                    format,
                    profile,
                    sourceFilename,
                    lorebook,
                    parsed.rejectedEntryCount(),
                    parsed.diagnostics(),
                    activationPossible);
        } catch (IllegalArgumentException exception) {
            return LorebookImportResult.rejected(
                    format, profile, sourceFilename, LorebookImportDiagnosticCode.INVALID_ENTRY);
        }
    }

    private static boolean isCharacterCard(LorebookSourceFormat format) {
        return format == LorebookSourceFormat.CHARACTER_CARD_V1
                || format == LorebookSourceFormat.CHARACTER_CARD_V2
                || format == LorebookSourceFormat.CHARACTER_CARD_V3;
    }

    private static JsonObject cardData(JsonObject root, LorebookSourceFormat format) {
        JsonObject data = objectValue(root, "data");
        return data == null ? root : data;
    }

    private static ImportedCharacterCard parseCharacterCard(
            JsonObject data, Optional<ImportedLorebook> embeddedLorebook, boolean preserveV3Extensions) {
        List<String> sourceMetadata = preserveV3Extensions ? rawJsonValues(data.get("source")) : List.of();
        List<String> assetDescriptors = preserveV3Extensions ? rawJsonValues(data.get("assets")) : List.of();
        String sourceExtensions = preserveV3Extensions ? sourceExtensions(data) : "";
        return new ImportedCharacterCard(
                stringValue(data, "name"),
                stringValue(data, "description"),
                stringValue(data, "personality"),
                stringValue(data, "scenario"),
                stringValue(data, "first_mes"),
                extractKeywords(arrayValue(data, "alternate_greetings")),
                stringValue(data, "mes_example"),
                stringValue(data, "creator"),
                stringValue(data, "character_version"),
                extractKeywords(arrayValue(data, "tags")),
                stringValue(data, "system_prompt"),
                stringValue(data, "post_history_instructions"),
                stringValue(data, "creator_notes"),
                stringValue(data, "nickname"),
                embeddedLorebook,
                sourceMetadata,
                assetDescriptors,
                sourceExtensions);
    }

    private static List<String> rawJsonValues(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return List.of();
        }
        if (!value.isJsonArray()) {
            return List.of(value.toString());
        }
        ArrayList<String> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            result.add(element.toString());
        }
        return List.copyOf(result);
    }

    private static String sourceExtensions(JsonObject data) {
        JsonObject extensions = data.deepCopy();
        for (String known : List.of(
                "name",
                "description",
                "personality",
                "scenario",
                "first_mes",
                "alternate_greetings",
                "mes_example",
                "creator",
                "character_version",
                "tags",
                "system_prompt",
                "post_history_instructions",
                "creator_notes",
                "nickname",
                "source",
                "assets",
                "character_book")) {
            extensions.remove(known);
        }
        return extensions.size() == 0 ? "" : extensions.toString();
    }

    private static JsonElement findEntries(JsonObject root) {
        try {
            return locateEntries(root);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ParsedImportedEntries parseEntries(JsonElement entries, CompatibilityProfile profile) {
        if (entries == null) {
            return new ParsedImportedEntries(List.of(), 0, List.of());
        }
        if (entries.isJsonObject()) {
            return parseImportedEntries(entries.getAsJsonObject(), profile);
        }
        if (entries.isJsonArray()) {
            return parseImportedEntries(entries.getAsJsonArray(), profile);
        }
        return new ParsedImportedEntries(List.of(), 1, List.of(error(LorebookImportDiagnosticCode.INVALID_ENTRY)));
    }

    private static ImportedLorebook createImportedLorebook(
            JsonObject root,
            LorebookSourceFormat format,
            CompatibilityProfile profile,
            List<ImportedLorebookEntry> entries) {
        JsonObject settings = bookSettings(root);
        int sourceVersion = integerValue(root, 0, "version", "spec_version");
        int defaultScanDepth = integerValue(settings, LorebookMatchOptions.DEFAULT_SCAN_DEPTH, "scanDepth");
        boolean recursiveScanning = booleanValue(settings, false, "recursiveScanning", "recursive");
        int maxRecursionSteps = integerValue(settings, 0, "maxRecursionSteps", "max_recursion_steps");
        return new ImportedLorebook(
                format, profile, sourceVersion, defaultScanDepth, recursiveScanning, maxRecursionSteps, entries);
    }

    private static ParsedImportedEntries analyzeUnsupportedFeatures(ParsedImportedEntries parsed) {
        ArrayList<LorebookImportDiagnostic> diagnostics = new ArrayList<>(parsed.diagnostics());
        boolean hasRegex = parsed.entries().stream().anyMatch(entry -> entry.matchOptions().regex());
        boolean hasUnknownRole = parsed.entries().stream()
                .anyMatch(entry -> entry.insertion().role() == LorebookPromptRole.UNKNOWN);
        boolean hasUnsupportedInsertion = parsed.entries().stream()
                .anyMatch(entry -> !LorebookPromptRegionPlanner.supports(entry.insertion()));
        if (hasRegex) {
            diagnostics.add(warning(LorebookImportDiagnosticCode.UNSUPPORTED_REGEX));
        }
        if (hasUnknownRole || hasUnsupportedInsertion) {
            diagnostics.add(warning(LorebookImportDiagnosticCode.UNSUPPORTED_FEATURE));
        }
        return new ParsedImportedEntries(parsed.entries(), parsed.rejectedEntryCount(), List.copyOf(diagnostics));
    }

    private static boolean activationPossible(ParsedImportedEntries parsed) {
        return parsed.rejectedEntryCount() == 0
                && parsed.diagnostics().stream().noneMatch(
                        diagnostic -> diagnostic.severity() == LorebookImportDiagnosticSeverity.ERROR
                                || diagnostic.code() == LorebookImportDiagnosticCode.UNSUPPORTED_REGEX
                                || diagnostic.code() == LorebookImportDiagnosticCode.UNSUPPORTED_FEATURE);
    }

    private static ParsedImportedEntries parseImportedEntries(JsonObject entries, CompatibilityProfile profile) {
        ArrayList<ImportedLorebookEntry> accepted = new ArrayList<>();
        ArrayList<LorebookImportDiagnostic> diagnostics = new ArrayList<>();
        int rejected = 0;
        int sourceOrder = 0;
        for (String key : entries.keySet()) {
            JsonElement element = entries.get(key);
            if (element == null || !element.isJsonObject()) {
                rejected++;
                diagnostics.add(error(LorebookImportDiagnosticCode.INVALID_ENTRY));
            } else if (!tryAddImportedEntry(accepted, diagnostics, element.getAsJsonObject(), key, sourceOrder, profile)) {
                rejected++;
            }
            sourceOrder++;
        }
        return new ParsedImportedEntries(List.copyOf(accepted), rejected, List.copyOf(diagnostics));
    }

    private static ParsedImportedEntries parseImportedEntries(JsonArray entries, CompatibilityProfile profile) {
        ArrayList<ImportedLorebookEntry> accepted = new ArrayList<>();
        ArrayList<LorebookImportDiagnostic> diagnostics = new ArrayList<>();
        int rejected = 0;
        for (int index = 0; index < entries.size(); index++) {
            JsonElement element = entries.get(index);
            if (element == null || !element.isJsonObject()) {
                rejected++;
                diagnostics.add(error(LorebookImportDiagnosticCode.INVALID_ENTRY));
            } else if (!tryAddImportedEntry(
                    accepted, diagnostics, element.getAsJsonObject(), Integer.toString(index), index, profile)) {
                rejected++;
            }
        }
        return new ParsedImportedEntries(List.copyOf(accepted), rejected, List.copyOf(diagnostics));
    }

    private static boolean tryAddImportedEntry(
            List<ImportedLorebookEntry> accepted,
            List<LorebookImportDiagnostic> diagnostics,
            JsonObject entryObj,
            String fallbackId,
            int sourceOrder,
            CompatibilityProfile profile) {
        if (accepted.size() >= LorebookLimits.MAX_ENTRIES_PER_LOREBOOK) {
            diagnostics.add(error(LorebookImportDiagnosticCode.ENTRY_LIMIT_EXCEEDED));
            return false;
        }
        try {
            boolean constant = booleanValue(entryObj, false, "constant");
            boolean enabled = entryObj.has("disable")
                    ? !booleanValue(entryObj, false, "disable")
                    : booleanValue(entryObj, true, "enabled");
            boolean selective = booleanValue(entryObj, false, "selective");
            boolean caseSensitive = booleanValue(
                    entryObj, profile.defaultCaseSensitive(), "caseSensitive", "case_sensitive");
            boolean wholeWord = booleanValue(
                    entryObj, profile.defaultWholeWord(), "matchWholeWords", "match_whole_words");
            boolean regex = booleanValue(entryObj, false, "useRegex", "use_regex");
            int scanDepth = integerValue(entryObj, LorebookMatchOptions.DEFAULT_SCAN_DEPTH, "scanDepth", "scan_depth");
            List<String> primaryKeys = extractKeywords(arrayValue(entryObj, "key", "keys"));
            List<String> secondaryKeys = extractKeywords(arrayValue(
                    entryObj, "keysecondary", "secondary_keys", "secondaryKeys"));
            String content = stringValue(entryObj, "content");
            String id = stringValue(entryObj, "id");
            if (id.isBlank()) {
                id = fallbackId;
            }
            LorebookInsertion insertion = new LorebookInsertion(
                    integerValue(entryObj, 0, "position"),
                    integerValue(entryObj, 0, "depth"),
                    promptRole(entryObj),
                    stringValue(entryObj, "outletName", "outlet_name"));
            LorebookActivationState activationState = new LorebookActivationState(
                    integerValue(entryObj, 100, "probability"),
                    integerValue(entryObj, 0, "sticky", "stickyTurns"),
                    integerValue(entryObj, 0, "cooldown", "cooldownTurns"),
                    integerValue(entryObj, 0, "delay", "delayTurns"),
                    stringValue(entryObj, "group"),
                    integerValue(entryObj, 1, "groupWeight", "group_weight"));
            LorebookRecursionOptions recursionOptions = new LorebookRecursionOptions(
                    booleanValue(entryObj, false, "recursive"),
                    booleanValue(entryObj, false, "preventRecursion", "prevent_recursion"),
                    booleanValue(entryObj, false, "excludeRecursion", "exclude_recursion"),
                    booleanValue(entryObj, false, "delayUntilRecursion", "delay_until_recursion"));
            ImportedLorebookEntry entry = new ImportedLorebookEntry(
                    id,
                    sourceOrder,
                    primaryKeys,
                    secondaryKeys,
                    content,
                    integerValue(entryObj, 100, "order", "insertion_order"),
                    enabled,
                    constant,
                    selective,
                    secondaryKeyLogic(entryObj),
                    new LorebookMatchOptions(caseSensitive, wholeWord, regex, scanDepth),
                    insertion,
                    activationState,
                    recursionOptions);
            accepted.add(entry);
            return true;
        } catch (RuntimeException exception) {
            diagnostics.add(error(LorebookImportDiagnosticCode.INVALID_ENTRY));
            return false;
        }
    }

    private static JsonObject bookSettings(JsonObject root) {
        JsonObject data = objectValue(root, "data");
        if (data != null) {
            JsonObject lorebook = objectValue(data, "lorebook");
            if (lorebook != null) {
                return lorebook;
            }
        }
        JsonObject lorebook = objectValue(root, "lorebook");
        return lorebook == null ? root : lorebook;
    }

    private static LorebookSourceFormat detectFormat(JsonObject root) {
        JsonElement type = root.get("type");
        if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString() && root.has("version")) {
            return switch (type.getAsString()) {
                case "marinara_lorebook" -> LorebookSourceFormat.MARINARA_LOREBOOK;
                case "marinara_character" -> LorebookSourceFormat.MARINARA_CHARACTER;
                default -> LorebookSourceFormat.UNSUPPORTED;
            };
        }
        JsonElement spec = root.get("spec");
        if (spec != null && spec.isJsonPrimitive() && spec.getAsJsonPrimitive().isString()) {
            return switch (spec.getAsString()) {
                case "chara_card_v2" -> LorebookSourceFormat.CHARACTER_CARD_V2;
                case "chara_card_v3" -> LorebookSourceFormat.CHARACTER_CARD_V3;
                default -> LorebookSourceFormat.UNSUPPORTED;
            };
        }
        if (isLegacyCharacterCard(root)) {
            return LorebookSourceFormat.CHARACTER_CARD_V1;
        }
        if (root.has("entries")) {
            return LorebookSourceFormat.CLASSIC_WORLD_INFO;
        }
        if (objectValue(root, "character_book") != null
                || (objectValue(root, "data") != null && objectValue(objectValue(root, "data"), "character_book") != null)) {
            return LorebookSourceFormat.CLASSIC_WORLD_INFO;
        }
        return LorebookSourceFormat.UNSUPPORTED;
    }

    private static boolean isLegacyCharacterCard(JsonObject root) {
        return root.has("name") && (root.has("description") || root.has("personality") || root.has("first_mes"));
    }

    private static JsonElement locateEntries(JsonObject root) {
        if (root.has("type") && root.has("version")) {
            JsonElement type = root.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()) {
                if ("marinara_lorebook".equals(type.getAsString())) {
                    JsonObject data = objectValue(root, "data");
                    if (data != null && data.has("entries")) {
                        return data.get("entries");
                    }
                    throw new IllegalArgumentException("Marinara lorebook export has no entries.");
                }
                if (type.getAsString().startsWith("marinara_")
                        && !"marinara_character".equals(type.getAsString())) {
                    throw new IllegalArgumentException("Marinara export is not a lorebook.");
                }
            }
        }
        if (root.has("entries")) {
            return root.get("entries");
        }
        JsonObject rootBook = objectValue(root, "character_book");
        if (rootBook != null && rootBook.has("entries")) {
            return rootBook.get("entries");
        }
        JsonObject data = objectValue(root, "data");
        JsonObject embeddedBook = data == null ? null : objectValue(data, "character_book");
        if (embeddedBook != null && embeddedBook.has("entries")) {
            return embeddedBook.get("entries");
        }
        throw new IllegalArgumentException("JSON must contain World Info or character-book entries.");
    }

    private static JsonObject objectValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray arrayValue(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonArray()) {
                return value.getAsJsonArray();
            }
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                JsonArray singleton = new JsonArray();
                singleton.add(value.getAsString());
                return singleton;
            }
        }
        return new JsonArray();
    }

    private static boolean booleanValue(JsonObject object, boolean fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsBoolean();
            }
        }
        return fallback;
    }

    private static int integerValue(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsInt();
            }
        }
        return fallback;
    }

    private static LorebookPromptRole promptRole(JsonObject entry) {
        JsonElement value = entry.get("role");
        if (value == null || value.isJsonNull()) {
            return LorebookPromptRole.SYSTEM;
        }
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Lorebook role must be a number or name.");
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return LorebookPromptRole.fromSourceValue(value.getAsInt());
        }
        if (!value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Lorebook role must be a number or name.");
        }
        return switch (value.getAsString().trim().toLowerCase(Locale.ROOT)) {
            case "system" -> LorebookPromptRole.SYSTEM;
            case "user" -> LorebookPromptRole.USER;
            case "assistant" -> LorebookPromptRole.ASSISTANT;
            default -> LorebookPromptRole.UNKNOWN;
        };
    }

    private static SecondaryKeyLogic secondaryKeyLogic(JsonObject entry) {
        JsonElement value = entry.get("selectiveLogic");
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return SecondaryKeyLogic.AND_ANY;
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return SecondaryKeyLogic.fromSillyTavernValue(value.getAsInt());
        }
        if (!value.getAsJsonPrimitive().isString()) {
            return SecondaryKeyLogic.AND_ANY;
        }
        return switch (value.getAsString()) {
            case "and", "or" -> SecondaryKeyLogic.AND_ANY;
            case "and_all" -> SecondaryKeyLogic.AND_ALL;
            case "not" -> SecondaryKeyLogic.NOT_ANY;
            case "not_all" -> SecondaryKeyLogic.NOT_ALL;
            default -> SecondaryKeyLogic.AND_ANY;
        };
    }

    private static String stringValue(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsString();
            }
        }
        return "";
    }

    private static List<String> extractKeywords(JsonArray values) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (JsonElement keywordEl : values) {
            if (!keywordEl.isJsonPrimitive() || !keywordEl.getAsJsonPrimitive().isString()) {
                continue;
            }
            String keyword = keywordEl.getAsString();
            if (!keyword.isBlank()) {
                keywords.add(keyword);
            }
        }
        return List.copyOf(keywords);
    }

    private static LorebookImportDiagnostic error(LorebookImportDiagnosticCode code) {
        return new LorebookImportDiagnostic(code, LorebookImportDiagnosticSeverity.ERROR);
    }

    private static LorebookImportDiagnostic warning(LorebookImportDiagnosticCode code) {
        return new LorebookImportDiagnostic(code, LorebookImportDiagnosticSeverity.WARNING);
    }

    private static String sourceFilename(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? "input" : fileName.toString();
    }

    private static byte[] readBoundedBytes(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > LorebookLimits.MAX_RAW_INPUT_BYTES) {
            throw new InputTooLargeException();
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > LorebookLimits.MAX_RAW_INPUT_BYTES) {
            throw new InputTooLargeException();
        }
        return bytes;
    }

    private record ParsedImportedEntries(
            List<ImportedLorebookEntry> entries,
            int rejectedEntryCount,
            List<LorebookImportDiagnostic> diagnostics) {
    }

    private static final class InputTooLargeException extends IOException {
    }
}
