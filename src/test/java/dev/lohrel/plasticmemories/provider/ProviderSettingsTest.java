package dev.lohrel.plasticmemories.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ProviderSettingsTest {
    @Test
    void expandsAnOpenAiCompatibleBaseUrlToChatCompletions() {
        ProviderSettings settings = ProviderSettings.create(
                "https://nano-gpt.com/api/v1", "example-model", "secret");

        assertEquals(
                "https://nano-gpt.com/api/v1/chat/completions",
                settings.chatCompletionsEndpoint().toString());
    }

    @Test
    void acceptsAnHttpsOpenAiCompatibleEndpoint() {
        ProviderSettings settings = ProviderSettings.create(
                "https://example.com/v1/chat/completions", "example-model", "secret");

        assertEquals("https://example.com/v1/chat/completions", settings.endpoint().toString());
        assertEquals("example-model", settings.model());
        assertEquals("secret", settings.apiKey());
    }

    @Test
    void rejectsPlainHttpForRemoteHosts() {
        assertThrows(IllegalArgumentException.class, () -> ProviderSettings.create(
                "http://example.com/v1/chat/completions", "example-model", "secret"));
    }
}
