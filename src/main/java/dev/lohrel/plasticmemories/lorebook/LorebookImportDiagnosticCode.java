package dev.lohrel.plasticmemories.lorebook;

/** Why an import or entry was rejected or flagged. */
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
