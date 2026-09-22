package dev.lohrel.plasticmemories.provider;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Endpoint, model and API key the player configured. */
public record ProviderSettings(URI endpoint, String model, String apiKey) {
    public static final int MAX_ENDPOINT_LENGTH = 2048;
    public static final int MAX_MODEL_LENGTH = 128;
    public static final int MAX_API_KEY_LENGTH = 4096;

    public ProviderSettings {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
    }

    public static ProviderSettings create(String endpointText, String modelText, String apiKey) {
        Objects.requireNonNull(endpointText, "endpointText");
        Objects.requireNonNull(modelText, "modelText");
        Objects.requireNonNull(apiKey, "apiKey");

        String endpointValue = endpointText.trim();
        String model = modelText.trim();
        if (endpointValue.isEmpty() || endpointValue.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException("Endpoint is required and must be at most 2048 characters.");
        }
        if (model.isEmpty() || model.length() > MAX_MODEL_LENGTH) {
            throw new IllegalArgumentException("Model is required and must be at most 128 characters.");
        }
        if (apiKey.length() > MAX_API_KEY_LENGTH) {
            throw new IllegalArgumentException("API key must be at most 4096 characters.");
        }

        URI endpoint;
        try {
            endpoint = URI.create(endpointValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Endpoint is not a valid URL.");
        }

        String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
        String host = endpoint.getHost();
        if (host == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Endpoint must be an absolute URL without credentials or a fragment.");
        }
        // Plain HTTP would send the API key unencrypted; allow it only for local servers like Ollama.
        if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopbackHost(host))) {
            throw new IllegalArgumentException("Remote endpoints must use HTTPS; HTTP is allowed only for loopback hosts.");
        }

        return new ProviderSettings(endpoint, model, apiKey);
    }

    /** Accepts either a base URL (".../v1") or the full ".../chat/completions" URL. */
    public URI chatCompletionsEndpoint() {
        String value = endpoint.toString();
        int queryIndex = value.indexOf('?');
        String query = queryIndex >= 0 ? value.substring(queryIndex) : "";
        String base = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        if (base.endsWith("/chat/completions")) {
            return endpoint;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/chat/completions" + query);
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }
}
