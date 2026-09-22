package dev.lohrel.plasticmemories.provider;

import java.util.concurrent.CompletableFuture;

/** An LLM backend. Returns the raw reply text; parsing happens in ModelReplyParser. */
public interface AiProvider {
    CompletableFuture<String> reply(ProviderRequest request);
}
