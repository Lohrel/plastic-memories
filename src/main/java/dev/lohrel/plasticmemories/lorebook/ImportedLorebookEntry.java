package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;

/**
 * One source entry after normalization. Aliases remain attached to this one
 * stable entry instead of being copied into multiple prompt fragments.
 */
public record ImportedLorebookEntry(
        String id,
        int sourceOrder,
        List<String> primaryKeys,
        List<String> secondaryKeys,
        String content,
        int order,
        boolean enabled,
        boolean constant,
        boolean selective,
        SecondaryKeyLogic secondaryKeyLogic,
        LorebookMatchOptions matchOptions,
        LorebookInsertion insertion,
        LorebookActivationState activationState,
        LorebookRecursionOptions recursionOptions) {
    public ImportedLorebookEntry {
        id = Objects.requireNonNull(id, "id");
        primaryKeys = List.copyOf(Objects.requireNonNull(primaryKeys, "primaryKeys"));
        secondaryKeys = List.copyOf(Objects.requireNonNull(secondaryKeys, "secondaryKeys"));
        content = Objects.requireNonNull(content, "content");
        secondaryKeyLogic = Objects.requireNonNull(secondaryKeyLogic, "secondaryKeyLogic");
        matchOptions = Objects.requireNonNull(matchOptions, "matchOptions");
        insertion = Objects.requireNonNull(insertion, "insertion");
        activationState = Objects.requireNonNull(activationState, "activationState");
        recursionOptions = Objects.requireNonNull(recursionOptions, "recursionOptions");
        if (id.isBlank() || id.length() > 256 || sourceOrder < 0) {
            throw new IllegalArgumentException("Invalid imported lorebook entry identity.");
        }
        validateKeywords(primaryKeys, !constant, "primary");
        validateKeywords(secondaryKeys, false, "secondary");
        if (content.isBlank() || content.length() > LorebookLimits.MAX_ENTRY_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Imported lorebook content is out of bounds.");
        }
        if (order < LorebookLimits.MIN_ENTRY_ORDER || order > LorebookLimits.MAX_ENTRY_ORDER) {
            throw new IllegalArgumentException("Imported lorebook order is out of bounds.");
        }
    }

    private static void validateKeywords(List<String> values, boolean required, String kind) {
        if (values.size() > LorebookLimits.MAX_KEYWORDS_PER_ENTRY || (required && values.isEmpty())) {
            throw new IllegalArgumentException("Invalid imported " + kind + " key count.");
        }
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > LorebookLimits.MAX_KEYWORD_LENGTH) {
                throw new IllegalArgumentException("Invalid imported " + kind + " key.");
            }
        }
    }
}
