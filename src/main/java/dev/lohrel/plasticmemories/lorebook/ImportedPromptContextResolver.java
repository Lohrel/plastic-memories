package dev.lohrel.plasticmemories.lorebook;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Scans the remembered turns plus the new message. The evaluator holds this conversation's
     * sticky/cooldown timers, so the caller keeps one per conversation.
     */
    public static ImportedPromptContext resolve(
            ClientLorebookContext activeContext,
            ConversationMemory memory,
            String currentMessage,
            ImportedLorebookEvaluator evaluator,
            int privateMessageCount) {
        Objects.requireNonNull(currentMessage, "currentMessage");
        ArrayList<String> scanWindow = new ArrayList<>();
        if (memory != null) {
            for (var turn : memory.turns()) {
                scanWindow.add(turn.playerMessage());
                scanWindow.add(turn.npcReply());
            }
        }
        scanWindow.add(currentMessage);
        return resolve(activeContext, List.copyOf(scanWindow), evaluator, privateMessageCount);
    }

    /** Lower-level form that takes the text to scan directly. */
    public static ImportedPromptContext resolve(
            ClientLorebookContext activeContext,
            List<String> scanWindow,
            ImportedLorebookEvaluator evaluator,
            int privateMessageCount) {
        Objects.requireNonNull(activeContext, "activeContext");
        Objects.requireNonNull(evaluator, "evaluator");
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (ImportedLorebook lorebook : activeContext.lorebooks()) {
            ImportedLorebook usable = withoutSkippedEntries(lorebook);
            for (ImportedLorebookEntry entry : evaluator.select(usable, scanWindow, privateMessageCount)) {
                candidates.add(new Candidate(usable, new ImportedPromptContext.PlacedLoreEntry(
                        entry, LorebookPlacement.of(lorebook.profile(), entry))));
            }
        }
        // One priority order across all books, so the budget keeps the most important entries.
        // Stable sort: equal orders keep book order (card book, this NPC's books, then global ones).
        candidates.sort(Comparator.comparingInt((Candidate candidate) -> candidate.placed().entry().order()).reversed());

        ArrayList<ImportedPromptContext.PlacedLoreEntry> sent = new ArrayList<>();
        Map<ImportedLorebook, List<ImportedLorebookEntry>> sentByBook = new LinkedHashMap<>();
        int characters = 0;
        for (Candidate candidate : candidates) {
            int length = candidate.placed().entry().content().length();
            if (sent.size() >= LorebookLimits.MAX_PROMPT_LORE_ENTRIES
                    || characters + length > LorebookLimits.MAX_PROMPT_LORE_CHARACTERS) {
                continue;
            }
            sent.add(candidate.placed());
            sentByBook.computeIfAbsent(candidate.book(), ignored -> new ArrayList<>()).add(candidate.placed().entry());
            characters += length;
        }
        // Only now start sticky/cooldown timers: an entry cut by the budget was never seen by the model.
        sentByBook.forEach((book, entries) -> evaluator.recordActivations(book, entries, privateMessageCount));
        return new ImportedPromptContext(activeContext.card(), sent);
    }

    private record Candidate(ImportedLorebook book, ImportedPromptContext.PlacedLoreEntry placed) {
    }

    /**
     * Skipped entries are removed before evaluation, so they can't win inclusion groups, feed
     * recursion or start timers. The copy is value-equal each time, so the evaluator's timers still match.
     */
    private static ImportedLorebook withoutSkippedEntries(ImportedLorebook book) {
        List<ImportedLorebookEntry> usable = book.entries().stream()
                .filter(entry -> LorebookPlacement.of(book.profile(), entry) != LorebookPlacement.UNSUPPORTED)
                .toList();
        if (usable.size() == book.entries().size()) {
            return book;
        }
        return new ImportedLorebook(book.format(), book.profile(), book.sourceVersion(), book.defaultScanDepth(),
                book.recursiveScanning(), book.maxRecursionSteps(), usable);
    }
}
