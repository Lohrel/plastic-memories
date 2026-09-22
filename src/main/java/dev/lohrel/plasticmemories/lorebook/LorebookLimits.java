package dev.lohrel.plasticmemories.lorebook;

/** Shared resource limits for untrusted client-local artifacts. */
public final class LorebookLimits {
    public static final long MAX_RAW_INPUT_BYTES = 10_485_760L;
    public static final long MAX_DECOMPRESSED_INPUT_BYTES = 10_485_760L;
    public static final int MAX_ARCHIVE_ENTRIES = 128;
    public static final long MAX_NORMALIZED_DOCUMENT_BYTES = 10_485_760L;
    public static final int MAX_ENTRIES_PER_LOREBOOK = 100;
    public static final int MAX_KEYWORDS_PER_ENTRY = 256;
    public static final int MAX_KEYWORD_LENGTH = 64;
    public static final int MAX_ENTRY_CONTENT_LENGTH = 8_192;
    public static final int MIN_ENTRY_ORDER = 0;
    public static final int MAX_ENTRY_ORDER = 10_000;
    public static final int MAX_RECURSION_SWEEPS = 128;

    private LorebookLimits() {
    }
}
