package dev.lohrel.plasticmemories.provider;

import java.util.concurrent.CompletableFuture;

public interface AiProvider {
    CompletableFuture<String> reply(ProviderRequest request);
}
