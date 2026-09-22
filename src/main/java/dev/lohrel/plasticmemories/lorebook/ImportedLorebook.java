package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;

/** An imported lorebook, converted to the mod's own format. */
public record ImportedLorebook(
        LorebookSourceFormat format,
        CompatibilityProfile profile,
        int sourceVersion,
        int defaultScanDepth,
        boolean recursiveScanning,
        int maxRecursionSteps,
        List<ImportedLorebookEntry> entries) {
    public ImportedLorebook {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(profile, "profile");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (format == LorebookSourceFormat.UNSUPPORTED || sourceVersion < 0
                || defaultScanDepth < 0 || defaultScanDepth > LorebookMatchOptions.MAX_SCAN_DEPTH
                || maxRecursionSteps < 0 || maxRecursionSteps > LorebookLimits.MAX_RECURSION_SWEEPS
                || entries.size() > LorebookLimits.MAX_ENTRIES_PER_LOREBOOK) {
            throw new IllegalArgumentException("Invalid normalized lorebook.");
        }
    }

    public ImportedLorebook(
            LorebookSourceFormat format,
            CompatibilityProfile profile,
            int sourceVersion,
            int defaultScanDepth,
            boolean recursiveScanning,
            List<ImportedLorebookEntry> entries) {
        this(format, profile, sourceVersion, defaultScanDepth, recursiveScanning, 0, entries);
    }
}
