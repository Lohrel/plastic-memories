package dev.lohrel.plasticmemories.lorebook;

/** Recursion controls are represented explicitly so unsupported variants never disappear. */
public record LorebookRecursionOptions(
        boolean recursive,
        boolean preventRecursion,
        boolean excludeRecursion,
        boolean delayUntilRecursion) {
    public static final LorebookRecursionOptions DEFAULT =
            new LorebookRecursionOptions(false, false, false, false);
}
