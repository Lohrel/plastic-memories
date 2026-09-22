package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;

/** A code plus severity. Holds no file content, so it's safe to show or log. */
public record LorebookImportDiagnostic(
        LorebookImportDiagnosticCode code,
        LorebookImportDiagnosticSeverity severity) {
    public LorebookImportDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
    }
}
