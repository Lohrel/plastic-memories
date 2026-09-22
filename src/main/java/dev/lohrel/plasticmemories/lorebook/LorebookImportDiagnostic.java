package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** Deliberately contains no imported text or parser exception details. */
public record LorebookImportDiagnostic(
        LorebookImportDiagnosticCode code,
        LorebookImportDiagnosticSeverity severity) {
    public LorebookImportDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
    }
}
