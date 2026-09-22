package dev.lohrel.plasticmemories.lorebook;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Assembles one provider-only imported context from private conversation state. */
public final class ImportedLorebookPromptContextFactory {
    private ImportedLorebookPromptContextFactory() {
    }

    public static ImportedPromptContext create(
            ClientLorebookContext activeContext, ConversationMemory memory, String currentMessage) {
        return create(
                activeContext,
                memory,
                currentMessage,
                new ImportedLorebookEvaluator(java.util.concurrent.ThreadLocalRandom.current()),
                privateMessageCount(memory));
    }

    /**
     * Uses the caller-owned evaluator and a real private-message sequence
     * number so timed source behavior remains confined to that local scope.
     */
    public static ImportedPromptContext create(
            ClientLorebookContext activeContext,
            ConversationMemory memory,
            String currentMessage,
            ImportedLorebookEvaluator evaluator,
            int privateMessageCount) {
        Objects.requireNonNull(activeContext, "activeContext");
        Objects.requireNonNull(currentMessage, "currentMessage");
        Objects.requireNonNull(evaluator, "evaluator");
        ArrayList<String> scanWindow = new ArrayList<>();
        if (memory != null) {
            for (var turn : memory.turns()) {
                scanWindow.add(turn.playerMessage());
                scanWindow.add(turn.npcReply());
            }
        }
        scanWindow.add(currentMessage);
        return ImportedPromptContextResolver.resolve(
                activeContext, List.copyOf(scanWindow), evaluator, privateMessageCount);
    }

    private static int privateMessageCount(ConversationMemory memory) {
        return memory == null ? 1 : memory.turns().size() + 1;
    }
}
