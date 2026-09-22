package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ImportedLorebookMatcherTest {
    @Test
    void activatesOneEnabledConstantAndOneSelectiveAliasMatchInStablePriorityOrder() {
        ImportedLorebookEntry constant = entry(
                "constant", List.of(), List.of(), 10, true, true, false, SecondaryKeyLogic.AND_ANY);
        ImportedLorebookEntry forest = entry(
                "forest", List.of("forest", "woods"), List.of("moon"), 5, true, false, true, SecondaryKeyLogic.AND_ALL);
        ImportedLorebookEntry disabled = entry(
                "disabled", List.of(), List.of(), 100, false, true, false, SecondaryKeyLogic.AND_ANY);
        ImportedLorebookEntry wrongSecondary = entry(
                "wrong-secondary", List.of("forest"), List.of("sun"), 20, true, false, true, SecondaryKeyLogic.AND_ALL);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(forest, disabled, constant, wrongSecondary));

        List<ImportedLorebookEntry> matches = ImportedLorebookMatcher.match(book, List.of("The moon watches the woods."));

        assertEquals(List.of(constant, forest), matches);
        assertEquals(List.of(constant), ImportedLorebookMatcher.match(book, List.of()));
    }

    private static ImportedLorebookEntry entry(
            String id,
            List<String> primaryKeys,
            List<String> secondaryKeys,
            int order,
            boolean enabled,
            boolean constant,
            boolean selective,
            SecondaryKeyLogic secondaryKeyLogic) {
        return new ImportedLorebookEntry(
                id,
                order,
                primaryKeys,
                secondaryKeys,
                id + " content",
                order,
                enabled,
                constant,
                selective,
                secondaryKeyLogic,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                LorebookActivationState.DEFAULT,
                LorebookRecursionOptions.DEFAULT);
    }
}
