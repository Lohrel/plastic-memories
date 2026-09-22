package dev.lohrel.plasticmemories.lorebook;

/** Stable, content-safe codes exposed by an import preview or report. */
public enum LorebookImportDiagnosticCode {
    INPUT_READ_FAILED,
    INPUT_TOO_LARGE,
    MALFORMED_JSON,
    MALFORMED_CONTAINER,
    UNSUPPORTED_FORMAT,
    INVALID_ROOT,
    INVALID_ENTRY,
    ENTRY_LIMIT_EXCEEDED,
    UNSUPPORTED_FEATURE,
    UNSUPPORTED_REGEX,
    UNKNOWN_SOURCE_EXTENSION
}
