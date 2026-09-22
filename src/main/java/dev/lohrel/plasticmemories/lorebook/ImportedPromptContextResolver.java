package dev.lohrel.plasticmemories.lorebook;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds provider-only imported context from explicitly active client-local lorebooks. */
public final class ImportedPromptContextResolver {
    private ImportedPromptContextResolver() {
    }

    public static ImportedPromptContext resolve(ClientLorebookContext activeContext, List<String> scanWindow) {
        return resolve(
                activeContext,
                scanWindow,
                new ImportedLorebookEvaluator(java.util.concurrent.ThreadLocalRandom.current()),
                1);
    }

    /**
     * Resolves one request with an evaluator owned by the caller's private
     * conversation scope. The evaluator carries only volatile client-local
     * activation state; it is never persisted or sent over the network.
     */
    public static ImportedPromptContext resolve(
            ClientLorebookContext activeContext,
            List<String> scanWindow,
            ImportedLorebookEvaluator evaluator,
            int privateMessageCount) {
        Objects.requireNonNull(activeContext, "activeContext");
        Objects.requireNonNull(evaluator, "evaluator");
        ArrayList<ImportedLorebookEntry> matchedEntries = new ArrayList<>();
        for (ImportedLorebook lorebook : activeContext.lorebooks()) {
            matchedEntries.addAll(evaluator.evaluate(lorebook, scanWindow, privateMessageCount));
        }
        return new ImportedPromptContext(activeContext.card(), matchedEntries);
    }
}
