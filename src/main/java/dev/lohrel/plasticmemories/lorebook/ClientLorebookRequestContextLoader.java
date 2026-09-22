package dev.lohrel.plasticmemories.lorebook;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Reads explicitly active client-local imports for one private provider request. */
public final class ClientLorebookRequestContextLoader {
    private static final int MAX_SCOPED_EVALUATORS = 128;

    private final ClientLorebookLibraryStore library;
    private final Map<LocalLorebookBindingKey, ScopedEvaluator> evaluators =
            new LinkedHashMap<>(MAX_SCOPED_EVALUATORS, 1.0F, true);

    public ClientLorebookRequestContextLoader(ClientLorebookLibraryStore library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    public synchronized ImportedPromptContext load(
            LocalLorebookBindingKey key, ConversationMemory memory, String currentMessage) {
        Objects.requireNonNull(key, "key");
        int rememberedTurns = memory == null ? 0 : memory.turns().size();
        ScopedEvaluator scoped = evaluatorFor(key, rememberedTurns);
        return ImportedLorebookPromptContextFactory.create(
                library.activeContext(key), memory, currentMessage, scoped.evaluator(), scoped.nextMessageCount());
    }

    /** Drops volatile activation state when a private conversation ends. */
    public synchronized void clear(LocalLorebookBindingKey key) {
        evaluators.remove(Objects.requireNonNull(key, "key"));
    }

    /** Drops all volatile activation state, for example on client disconnect. */
    public synchronized void clear() {
        evaluators.clear();
    }

    private ScopedEvaluator evaluatorFor(LocalLorebookBindingKey key, int rememberedTurns) {
        ScopedEvaluator scoped = evaluators.get(key);
        if (scoped == null || rememberedTurns < scoped.lastRememberedTurns()) {
            if (scoped == null && evaluators.size() >= MAX_SCOPED_EVALUATORS) {
                Iterator<LocalLorebookBindingKey> keys = evaluators.keySet().iterator();
                if (keys.hasNext()) {
                    keys.next();
                    keys.remove();
                }
            }
            scoped = new ScopedEvaluator(new ImportedLorebookEvaluator(ThreadLocalRandom.current()), rememberedTurns);
            evaluators.put(key, scoped);
        }
        scoped.observeRememberedTurns(rememberedTurns);
        return scoped;
    }

    private static final class ScopedEvaluator {
        private final ImportedLorebookEvaluator evaluator;
        private int lastRememberedTurns;
        private int privateMessageCount;

        private ScopedEvaluator(ImportedLorebookEvaluator evaluator, int rememberedTurns) {
            this.evaluator = evaluator;
            this.lastRememberedTurns = rememberedTurns;
            this.privateMessageCount = rememberedTurns;
        }

        private ImportedLorebookEvaluator evaluator() {
            return evaluator;
        }

        private int lastRememberedTurns() {
            return lastRememberedTurns;
        }

        private void observeRememberedTurns(int rememberedTurns) {
            lastRememberedTurns = rememberedTurns;
        }

        private int nextMessageCount() {
            return ++privateMessageCount;
        }
    }
}
