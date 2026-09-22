package dev.lohrel.plasticmemories.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.lohrel.plasticmemories.lorebook.ImportedPromptContext;
import dev.lohrel.plasticmemories.memory.ConversationMemory;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleProviderTest {
    @Test
    void exposesOnlyASafeAuthenticationFailureMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "sensitive provider body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ProviderSettings settings = ProviderSettings.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions",
                    "test-model",
                    "test-key");
            OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(Duration.ofSeconds(5));

            Exception failure = assertThrows(Exception.class, () -> provider
                    .reply(new PromptBuilder().build(settings, "John", ConversationMemory.empty(), "Hello",
                            CookAvailability.AVAILABLE, NpcProfile.empty(), ImportedPromptContext.empty()))
                    .get(5, TimeUnit.SECONDS));

            assertEquals("Provider authentication failed.", OpenAiCompatibleProvider.safeFailureMessage(failure));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsPrivateTextDirectlyToTheConfiguredProviderAndReturnsItsReply() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"Hello, friend.\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ProviderSettings settings = ProviderSettings.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions",
                    "test-model",
                    "test-key");
            OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(Duration.ofSeconds(5));

            ConversationMemory memory = ConversationMemory.empty().append(
                    "Do you remember me?", "Of course I remember you.");
            ProviderRequest request = new PromptBuilder().build(
                    settings,
                    "John",
                    memory,
                    "This is private",
                    CookAvailability.NO_FOOD,
                    NpcProfile.create(
                            "Village healer",
                            "Warm but blunt",
                            "Red coat",
                            "Raised near the old mine"),
                    ImportedPromptContext.empty());
            String reply = provider.reply(request).get(5, TimeUnit.SECONDS);

            assertEquals("Hello, friend.", reply);
            assertEquals("Bearer test-key", authorization.get());
            var json = JsonParser.parseString(requestBody.get()).getAsJsonObject();
            assertEquals("test-model", json.get("model").getAsString());
            assertEquals(0.0, json.get("temperature").getAsDouble());
            assertTrue(requestBody.get().contains("Do you remember me?"));
            assertEquals(
                    "REPLY: Of course I remember you.\nSKILL: NONE",
                    json.getAsJsonArray("messages").get(2).getAsJsonObject().get("content").getAsString());
            assertTrue(requestBody.get().contains("This is private"));
            assertTrue(requestBody.get().contains("COOK availability: NO_FOOD"));
            assertTrue(requestBody.get().contains("Description: Village healer"));
            assertTrue(requestBody.get().contains("Personality: Warm but blunt"));
            assertTrue(requestBody.get().contains("Appearance: Red coat"));
            assertTrue(requestBody.get().contains("Backstory: Raised near the old mine"));
        } finally {
            server.stop(0);
        }
    }
}
