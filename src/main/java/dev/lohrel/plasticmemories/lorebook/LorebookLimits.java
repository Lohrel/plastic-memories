package dev.lohrel.plasticmemories.lorebook;

/** Size limits for imported files, so a huge or malicious file can't exhaust memory. */
public final class LorebookLimits {
    public static final long MAX_RAW_INPUT_BYTES = 10_485_760L;
    public static final long MAX_DECOMPRESSED_INPUT_BYTES = 10_485_760L;
    public static final int MAX_ARCHIVE_ENTRIES = 128;
    public static final long MAX_NORMALIZED_DOCUMENT_BYTES = 10_485_760L;
    public static final int MAX_ENTRIES_PER_LOREBOOK = 2_000;
    public static final int MAX_KEYWORDS_PER_ENTRY = 256;
    public static final int MAX_KEYWORD_LENGTH = 256;
    public static final int MAX_ENTRY_CONTENT_LENGTH = 8_192;
    public static final int MIN_ENTRY_ORDER = 0;
    public static final int MAX_ENTRY_ORDER = 10_000;
    public static final int MAX_RECURSION_SWEEPS = 128;
    /** Lore per prompt, filled highest priority first like SillyTavern's budget; the rest waits for a later message. */
    public static final int MAX_PROMPT_LORE_ENTRIES = 32;
    public static final int MAX_PROMPT_LORE_CHARACTERS = 16_384;

    private LorebookLimits() {
    }
}
