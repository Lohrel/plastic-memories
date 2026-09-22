package dev.lohrel.plasticmemories.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Calls any OpenAI-compatible /chat/completions endpoint (OpenAI, OpenRouter, llama.cpp, LM Studio...). */
public final class OpenAiCompatibleProvider implements AiProvider {
    private static final int MAX_RESPONSE_BYTES = 65_536;
    private static final int MAX_REPLY_LENGTH = 4_096;

    private final HttpClient client;
    private final Duration requestTimeout;

    public OpenAiCompatibleProvider(Duration requestTimeout) {
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                // A redirect could forward the API key to another host.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public CompletableFuture<String> reply(ProviderRequest request) {
        Objects.requireNonNull(request, "request");
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", request.settings().model());
        requestJson.addProperty("max_tokens", 512);
        // TODO: temperature 0 keeps the REPLY/SKILL format reliable but makes roleplay flat. Revisit with the skill rework.
        requestJson.addProperty("temperature", 0.0);
        var messages = new com.google.gson.JsonArray();
        for (var message : request.messages()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", message.role());
            msg.addProperty("content", message.content());
            messages.add(msg);
        }
        requestJson.add("messages", messages);

        HttpRequest.Builder httpRequest = HttpRequest.newBuilder(request.settings().chatCompletionsEndpoint())
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString(), StandardCharsets.UTF_8));
        if (!request.settings().apiKey().isEmpty()) {
            httpRequest.header("Authorization", "Bearer " + request.settings().apiKey());
        }

        return client.sendAsync(httpRequest.build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(this::readReply);
    }

    private String readReply(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderException(switch (response.statusCode()) {
                    case 401, 403 -> "AUTHENTICATION_FAILED";
                    case 404 -> "ENDPOINT_NOT_FOUND";
                    case 429 -> "RATE_LIMITED";
                    default -> response.statusCode() >= 500 ? "PROVIDER_UNAVAILABLE" : "HTTP_ERROR";
                });
            }
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new ProviderException("RESPONSE_TOO_LARGE");
            }
            JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            String content = json.getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString()
                    .strip();
            if (content.isBlank() || content.length() > MAX_REPLY_LENGTH) {
                throw new ProviderException("INVALID_RESPONSE");
            }
            return content;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ProviderException providerException) {
                throw providerException;
            }
            throw new ProviderException("INVALID_RESPONSE");
        }
    }

    /** Turns any failure into a fixed message. Never echoes the provider's error body, which may contain prompt text. */
    public static String safeFailureMessage(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ProviderException providerFailure) {
                return switch (providerFailure.getMessage()) {
                    case "AUTHENTICATION_FAILED" -> "Provider authentication failed.";
                    case "ENDPOINT_NOT_FOUND" -> "Provider endpoint was not found.";
                    case "RATE_LIMITED" -> "Provider rate limit reached.";
                    case "PROVIDER_UNAVAILABLE" -> "Provider is temporarily unavailable.";
                    case "RESPONSE_TOO_LARGE" -> "Provider response was too large.";
                    case "INVALID_RESPONSE" -> "Provider returned an unsupported response.";
                    case "INVALID_REQUEST" -> "Private message could not be sent.";
                    default -> "Provider request failed.";
                };
            }
            current = current.getCause();
        }
        return "Provider network request failed.";
    }

    public static final class ProviderException extends RuntimeException {
        public ProviderException(String safeCode) {
            super(safeCode);
        }
    }
}
