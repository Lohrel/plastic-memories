package dev.lohrel.plasticmemories.lorebook;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runs the evaluator over every active lorebook and collects the card plus selected entries. */
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

    /** The evaluator holds this conversation's sticky/cooldown timers; they live in memory only. */
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
