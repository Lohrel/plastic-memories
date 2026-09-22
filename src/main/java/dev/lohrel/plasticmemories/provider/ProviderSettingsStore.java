package dev.lohrel.plasticmemories.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lohrel.plasticmemories.storage.PrivateJsonFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;

/** Reads and writes provider.json. The file is owner-only because it holds the API key. */
public final class ProviderSettingsStore {
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 16_384;
    private static final Set<String> KNOWN_FIELDS = Set.of("version", "endpoint", "model", "apiKey", "sampling");

    private final Path file;

    public ProviderSettingsStore(Path file) {
        this.file = file;
    }

    public Optional<ProviderSettings> load() {
        return PrivateJsonFile.read(file, MAX_FILE_BYTES, ProviderSettingsStore::parse);
    }

    public void save(ProviderSettings settings) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        json.addProperty("endpoint", settings.endpoint().toString());
        json.addProperty("model", settings.model());
        json.addProperty("apiKey", settings.apiKey());
        JsonObject sampling = new JsonObject();
        settings.sampling().forEach((parameter, value) -> sampling.addProperty(parameter.wireName(), value));
        json.add("sampling", sampling);
        // Keep fields a newer build wrote, so downgrading and saving doesn't strip them.
        PrivateJsonFile.read(file, MAX_FILE_BYTES, existing -> existing).ifPresent(existing -> {
            for (String key : existing.keySet()) {
                if (!KNOWN_FIELDS.contains(key)) {
                    json.add(key, existing.get(key));
                }
            }
        });
        PrivateJsonFile.write(file, json.toString());
    }

    private static ProviderSettings parse(JsonObject json) {
        PrivateJsonFile.requireVersion(json, FORMAT_VERSION);
        // "sampling" was added after v1 shipped; unknown or invalid values are dropped one by one.
        EnumMap<SamplingParameter, Double> sampling = new EnumMap<>(SamplingParameter.class);
        JsonElement stored = json.get("sampling");
        if (stored != null && stored.isJsonObject()) {
            for (String key : stored.getAsJsonObject().keySet()) {
                SamplingParameter.fromWireName(key).ifPresent(parameter -> {
                    try {
                        sampling.put(parameter, parameter.validate(stored.getAsJsonObject().get(key).getAsDouble()));
                    } catch (RuntimeException ignored) {
                        // Leave this one unset.
                    }
                });
            }
        }
        return ProviderSettings.create(
                json.get("endpoint").getAsString(),
                json.get("model").getAsString(),
                json.get("apiKey").getAsString())
                .withSampling(sampling);
    }
}
