package dev.lohrel.plasticmemories.lorebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Evaluates normalized imports only in client-local memory. A stateful instance
 * retains timed effects for one private conversation scope.
 */
public final class ImportedLorebookEvaluator {
    private final RandomGenerator random;
    private final Map<RuntimeEntryKey, TimedEffect> timedEffects = new HashMap<>();
    private int lastMessageCount = -1;

    public ImportedLorebookEvaluator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Evaluates a stateless first private message without bypassing delay settings. */
    public static List<ImportedLorebookEntry> evaluate(ImportedLorebook book, List<String> scanWindow) {
        return new ImportedLorebookEvaluator(ThreadLocalRandom.current()).evaluate(book, scanWindow, 1);
    }

    /** Random injection exists solely for deterministic client-local first-message tests. */
    public static List<ImportedLorebookEntry> evaluate(
            ImportedLorebook book, List<String> scanWindow, RandomGenerator random) {
        return new ImportedLorebookEvaluator(random).evaluate(book, scanWindow, 1);
    }

    /**
     * Evaluates at a monotonically increasing private-conversation message count.
     * A reset or rewind clears volatile timed effects instead of carrying them into
     * a different private transcript.
     */
    public List<ImportedLorebookEntry> evaluate(
            ImportedLorebook book, List<String> scanWindow, int privateMessageCount) {
        Objects.requireNonNull(book, "book");
        int messageCount = Math.max(0, privateMessageCount);
        if (messageCount < lastMessageCount) {
            timedEffects.clear();
        }
        lastMessageCount = messageCount;

        LinkedHashSet<ImportedLorebookEntry> directCandidates = activeStickyEntries(book, messageCount);
        for (ImportedLorebookEntry entry : ImportedLorebookMatcher.match(book, scanWindow)) {
            if (!entry.recursionOptions().delayUntilRecursion()) {
                directCandidates.add(entry);
            }
        }
        List<ImportedLorebookEntry> direct = activate(book, List.copyOf(directCandidates), messageCount);
        if (!book.recursiveScanning()) {
            return order(direct);
        }

        Set<ImportedLorebookEntry> selected = new LinkedHashSet<>(direct);
        List<String> recursiveWindow = recursionContent(direct);
        int remainingRecursiveSweeps = book.maxRecursionSteps() == 0
                ? book.entries().size()
                : Math.min(book.entries().size(), book.maxRecursionSteps() - 1);
        while (!recursiveWindow.isEmpty() && remainingRecursiveSweeps-- > 0) {
            List<ImportedLorebookEntry> candidates = ImportedLorebookMatcher.match(book, recursiveWindow).stream()
                    .filter(entry -> !entry.recursionOptions().excludeRecursion())
                    .filter(entry -> !selected.contains(entry))
                    .toList();
            List<ImportedLorebookEntry> recursivelyMatched = activate(book, candidates, messageCount);
            if (recursivelyMatched.isEmpty()) {
                break;
            }
            selected.addAll(recursivelyMatched);
            recursiveWindow = recursionContent(recursivelyMatched);
        }
        return order(selected);
    }

    private LinkedHashSet<ImportedLorebookEntry> activeStickyEntries(ImportedLorebook book, int messageCount) {
        LinkedHashSet<ImportedLorebookEntry> sticky = new LinkedHashSet<>();
        for (ImportedLorebookEntry entry : book.entries()) {
            if (entry.enabled() && !entry.matchOptions().regex() && timing(book, entry, messageCount).sticky()) {
                sticky.add(entry);
            }
        }
        return sticky;
    }

    private List<ImportedLorebookEntry> activate(
            ImportedLorebook book, List<ImportedLorebookEntry> candidates, int messageCount) {
        ArrayList<ImportedLorebookEntry> eligible = new ArrayList<>();
        for (ImportedLorebookEntry entry : candidates) {
            Timing timing = timing(book, entry, messageCount);
            if (timing.cooldown()) {
                continue;
            }
            if (timing.sticky()) {
                eligible.add(entry);
                continue;
            }
            if (messageCount < entry.activationState().delayTurns()) {
                continue;
            }
            if (passesProbability(entry.activationState().probabilityPercent())) {
                eligible.add(entry);
            }
        }
        List<ImportedLorebookEntry> selected = selectGroupWinners(book, eligible, messageCount);
        for (ImportedLorebookEntry entry : selected) {
            if (!timing(book, entry, messageCount).sticky()) {
                recordActivation(book, entry, messageCount);
            }
        }
        return selected;
    }

    private List<ImportedLorebookEntry> selectGroupWinners(
            ImportedLorebook book, List<ImportedLorebookEntry> eligible, int messageCount) {
        Map<String, List<ImportedLorebookEntry>> groups = new LinkedHashMap<>();
        for (ImportedLorebookEntry entry : eligible) {
            String group = entry.activationState().group().trim();
            if (!group.isEmpty()) {
                groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(entry);
            }
        }
        if (groups.isEmpty()) {
            return List.copyOf(eligible);
        }
        Set<ImportedLorebookEntry> winners = new HashSet<>();
        for (List<ImportedLorebookEntry> group : groups.values()) {
            winners.add(selectWeighted(book, group, messageCount));
        }
        return eligible.stream()
                .filter(entry -> entry.activationState().group().isBlank() || winners.contains(entry))
                .toList();
    }

    private ImportedLorebookEntry selectWeighted(
            ImportedLorebook book, List<ImportedLorebookEntry> candidates, int messageCount) {
        for (ImportedLorebookEntry candidate : candidates) {
            if (timing(book, candidate, messageCount).sticky()) {
                return candidate;
            }
        }
        int totalWeight = candidates.stream().mapToInt(entry -> entry.activationState().groupWeight()).sum();
        if (totalWeight <= 0) {
            return candidates.getFirst();
        }
        int roll = random.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (ImportedLorebookEntry candidate : candidates) {
            cumulativeWeight += candidate.activationState().groupWeight();
            if (roll < cumulativeWeight) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private boolean passesProbability(int probabilityPercent) {
        return probabilityPercent >= 100 || (probabilityPercent > 0 && random.nextInt(100) < probabilityPercent);
    }

    private Timing timing(ImportedLorebook book, ImportedLorebookEntry entry, int messageCount) {
        RuntimeEntryKey key = new RuntimeEntryKey(book, entry.id());
        TimedEffect effect = timedEffects.get(key);
        if (effect == null) {
            return Timing.NONE;
        }
        if (effect.stickyUntilMessage() >= messageCount) {
            return Timing.STICKY;
        }

        int cooldownUntil = effect.cooldownUntilMessage();
        if (cooldownUntil < 0 && entry.activationState().cooldownTurns() > 0) {
            cooldownUntil = effect.stickyUntilMessage() + entry.activationState().cooldownTurns();
            effect = new TimedEffect(-1, cooldownUntil);
            timedEffects.put(key, effect);
        }
        if (cooldownUntil >= messageCount) {
            return Timing.COOLDOWN;
        }
        timedEffects.remove(key);
        return Timing.NONE;
    }

    private void recordActivation(ImportedLorebook book, ImportedLorebookEntry entry, int messageCount) {
        LorebookActivationState activation = entry.activationState();
        if (activation.stickyTurns() > 0) {
            timedEffects.put(new RuntimeEntryKey(book, entry.id()), new TimedEffect(messageCount + activation.stickyTurns(), -1));
        } else if (activation.cooldownTurns() > 0) {
            timedEffects.put(new RuntimeEntryKey(book, entry.id()), new TimedEffect(-1, messageCount + activation.cooldownTurns()));
        }
    }

    private static List<ImportedLorebookEntry> order(Iterable<ImportedLorebookEntry> entries) {
        ArrayList<ImportedLorebookEntry> ordered = new ArrayList<>();
        entries.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(ImportedLorebookEntry::order)
                .reversed()
                .thenComparingInt(ImportedLorebookEntry::sourceOrder));
        return List.copyOf(ordered);
    }

    private static List<String> recursionContent(List<ImportedLorebookEntry> entries) {
        ArrayList<String> content = new ArrayList<>();
        for (ImportedLorebookEntry entry : entries) {
            if (entry.recursionOptions().recursive() && !entry.recursionOptions().preventRecursion()) {
                content.add(entry.content());
            }
        }
        return List.copyOf(content);
    }

    private record RuntimeEntryKey(ImportedLorebook book, String entryId) {
    }

    private record TimedEffect(int stickyUntilMessage, int cooldownUntilMessage) {
    }

    private enum Timing {
        NONE,
        STICKY,
        COOLDOWN;

        boolean sticky() {
            return this == STICKY;
        }

        boolean cooldown() {
            return this == COOLDOWN;
        }
    }
}
