package dev.lohrel.plasticmemories.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.lorebook.ImportedPromptContext;
import dev.lohrel.plasticmemories.memory.ConversationMemory;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SamplingSettingsTest {
    @TempDir
    Path directory;

    @Test
    void requestOnlyContainsTheParametersThePlayerSet() {
        ProviderSettings settings = base().withSampling(Map.of(
                SamplingParameter.TEMPERATURE, 0.9,
                SamplingParameter.TOP_K, 40.0));

        var json = OpenAiCompatibleProvider.requestJson(request(settings));

        assertEquals(0.9, json.get("temperature").getAsDouble());
        assertEquals("40", json.get("top_k").toString());
        assertFalse(json.has("top_p"));
        assertFalse(json.has("repetition_penalty"));
    }

    @Test
    void unsetTemperatureIsLeftToTheProviderAndMaxTokensHasADefault() {
        var json = OpenAiCompatibleProvider.requestJson(request(base()));

        assertFalse(json.has("temperature"));
        assertEquals(512, json.get("max_tokens").getAsInt());
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> base().withSampling(Map.of(SamplingParameter.TOP_P, 1.5)));
        assertThrows(IllegalArgumentException.class,
                () -> base().withSampling(Map.of(SamplingParameter.TOP_K, 2.5)));
    }

    @Test
    void parsesBlankAsUnsetAndRejectsGarbage() {
        assertTrue(SamplingParameter.TEMPERATURE.parse(" ").isEmpty());
        assertEquals(0.7, SamplingParameter.TEMPERATURE.parse("0.7").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> SamplingParameter.TEMPERATURE.parse("warm"));
    }

    @Test
    void samplingSurvivesSaveAndLoad() throws Exception {
        Path file = directory.resolve("provider.json");
        EnumMap<SamplingParameter, Double> sampling = new EnumMap<>(SamplingParameter.class);
        sampling.put(SamplingParameter.MIN_P, 0.05);
        sampling.put(SamplingParameter.MAX_TOKENS, 300.0);
        ProviderSettings settings = base().withSampling(sampling);

        new ProviderSettingsStore(file).save(settings);

        assertEquals(settings, new ProviderSettingsStore(file).load().orElseThrow());
    }

    @Test
    void savingKeepsFieldsFromANewerVersion() throws Exception {
        Path file = directory.resolve("provider.json");
        Files.writeString(file, """
                {"version":1,"endpoint":"https://example.com/v1","model":"m","apiKey":"k","fromTheFuture":true}
                """);

        new ProviderSettingsStore(file).save(base());

        assertTrue(Files.readString(file).contains("\"fromTheFuture\":true"));
    }

    private static ProviderSettings base() {
        return ProviderSettings.create("https://example.com/v1", "model", "key");
    }

    private static ProviderRequest request(ProviderSettings settings) {
        return new PromptBuilder().build(settings, "John", ConversationMemory.empty(), "Hi",
                CookAvailability.AVAILABLE, NpcProfile.empty(), ImportedPromptContext.empty());
    }
}
