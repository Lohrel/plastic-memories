package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Content-safe outcome returned for every import preview or activation attempt. */
public record LorebookImportResult(
        LorebookSourceFormat format,
        CompatibilityProfile profile,
        String sourceFilename,
        Optional<ImportedCharacterCard> characterCard,
        Optional<ImportedLorebook> lorebook,
        int acceptedEntryCount,
        int rejectedEntryCount,
        List<LorebookImportDiagnostic> diagnostics,
        boolean activationPossible) {
    public LorebookImportResult {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(profile, "profile");
        sourceFilename = Objects.requireNonNull(sourceFilename, "sourceFilename");
        characterCard = Objects.requireNonNull(characterCard, "characterCard");
        lorebook = Objects.requireNonNull(lorebook, "lorebook");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (sourceFilename.isBlank() || sourceFilename.length() > 255
                || acceptedEntryCount < 0 || rejectedEntryCount < 0) {
            throw new IllegalArgumentException("Invalid lorebook import result.");
        }
    }

    public static LorebookImportResult accepted(
            LorebookSourceFormat format,
            CompatibilityProfile profile,
            String sourceFilename,
            ImportedLorebook lorebook,
            int rejectedEntryCount,
            List<LorebookImportDiagnostic> diagnostics,
            boolean activationPossible) {
        return new LorebookImportResult(
                format,
                profile,
                sourceFilename,
                Optional.empty(),
                Optional.of(lorebook),
                lorebook.entries().size(),
                rejectedEntryCount,
                diagnostics,
                activationPossible);
    }

    public static LorebookImportResult acceptedCard(
            LorebookSourceFormat format,
            CompatibilityProfile profile,
            String sourceFilename,
            ImportedCharacterCard characterCard,
            Optional<ImportedLorebook> lorebook,
            int rejectedEntryCount,
            List<LorebookImportDiagnostic> diagnostics,
            boolean activationPossible) {
        return new LorebookImportResult(
                format,
                profile,
                sourceFilename,
                Optional.of(characterCard),
                lorebook,
                lorebook.map(value -> value.entries().size()).orElse(0),
                rejectedEntryCount,
                diagnostics,
                activationPossible);
    }

    public static LorebookImportResult rejected(
            LorebookSourceFormat format,
            CompatibilityProfile profile,
            String sourceFilename,
            LorebookImportDiagnosticCode code) {
        return new LorebookImportResult(
                format,
                profile,
                sourceFilename,
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                List.of(new LorebookImportDiagnostic(code, LorebookImportDiagnosticSeverity.ERROR)),
                false);
    }
}
