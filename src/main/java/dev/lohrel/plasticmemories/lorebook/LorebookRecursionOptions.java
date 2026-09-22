package dev.lohrel.plasticmemories.lorebook;

/** Controls whether an entry can trigger, or be triggered by, other entries' content. */
public record LorebookRecursionOptions(
        boolean recursive,
        boolean preventRecursion,
        boolean excludeRecursion,
        boolean delayUntilRecursion) {
    public static final LorebookRecursionOptions DEFAULT =
            new LorebookRecursionOptions(false, false, false, false);
}
