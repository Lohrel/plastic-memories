package dev.lohrel.plasticmemories.lorebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keyword matching only: which entries' keys appear in the text. Timing and groups are handled by the evaluator. */
public final class ImportedLorebookMatcher {
    private ImportedLorebookMatcher() {
    }

    public static List<ImportedLorebookEntry> match(ImportedLorebook book, List<String> scanWindow) {
        if (book == null || book.entries().isEmpty()) {
            return List.of();
        }
        List<String> window = scanWindow == null ? List.of() : scanWindow;
        ArrayList<ImportedLorebookEntry> matched = new ArrayList<>();
        Set<String> matchedIds = new HashSet<>();
        for (ImportedLorebookEntry entry : book.entries()) {
            if (!entry.enabled() || entry.matchOptions().regex()) {
                continue;
            }
            if (!entry.constant() && !matchesPrimary(entry, window, book.defaultScanDepth())) {
                continue;
            }
            if (entry.selective()
                    && !entry.secondaryKeys().isEmpty()
                    && !matchesSecondary(entry, window, book.defaultScanDepth())) {
                continue;
            }
            if (matchedIds.add(entry.id())) {
                matched.add(entry);
            }
        }
        matched.sort(Comparator.comparingInt(ImportedLorebookEntry::order)
                .reversed()
                .thenComparingInt(ImportedLorebookEntry::sourceOrder));
        return List.copyOf(matched);
    }

    private static boolean matchesPrimary(ImportedLorebookEntry entry, List<String> scanWindow, int bookScanDepth) {
        return matchesAny(
                scanWindow(entry, scanWindow, bookScanDepth),
                entry.primaryKeys(),
                entry.matchOptions().caseSensitive(),
                entry.matchOptions().wholeWord());
    }

    private static boolean matchesSecondary(ImportedLorebookEntry entry, List<String> scanWindow, int bookScanDepth) {
        List<String> scanned = scanWindow(entry, scanWindow, bookScanDepth);
        int matched = 0;
        for (String keyword : entry.secondaryKeys()) {
            if (matchesAny(
                    scanned,
                    List.of(keyword),
                    entry.matchOptions().caseSensitive(),
                    entry.matchOptions().wholeWord())) {
                matched++;
            }
        }
        return switch (entry.secondaryKeyLogic()) {
            case AND_ANY -> matched > 0;
            case NOT_ALL -> matched < entry.secondaryKeys().size();
            case NOT_ANY -> matched == 0;
            case AND_ALL -> matched == entry.secondaryKeys().size();
        };
    }

    private static List<String> scanWindow(ImportedLorebookEntry entry, List<String> scanWindow, int bookScanDepth) {
        int effectiveScanDepth = Math.min(bookScanDepth, entry.matchOptions().scanDepth());
        if (effectiveScanDepth <= 0 || scanWindow.isEmpty()) {
            return List.of();
        }
        return scanWindow.subList(Math.max(0, scanWindow.size() - effectiveScanDepth), scanWindow.size());
    }

    private static boolean matchesAny(
            List<String> scanned, List<String> keywords, boolean caseSensitive, boolean wholeWord) {
        for (String text : scanned) {
            if (text == null) {
                continue;
            }
            for (String keyword : keywords) {
                if (contains(text, keyword, caseSensitive, wholeWord)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(String text, String keyword, boolean caseSensitive, boolean wholeWord) {
        String haystack = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
        String needle = caseSensitive ? keyword : keyword.toLowerCase(Locale.ROOT);
        if (!wholeWord || needle.trim().split("\\s+").length > 1) {
            return haystack.contains(needle);
        }
        int position = haystack.indexOf(needle);
        while (position >= 0) {
            int after = position + needle.length();
            boolean hasLeftBoundary = position == 0 || !isWordCharacter(haystack.charAt(position - 1));
            boolean hasRightBoundary = after == haystack.length() || !isWordCharacter(haystack.charAt(after));
            if (hasLeftBoundary && hasRightBoundary) {
                return true;
            }
            position = haystack.indexOf(needle, position + 1);
        }
        return false;
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }
}
