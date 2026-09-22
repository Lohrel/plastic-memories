package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

final class ImportedLorebookEvaluatorTest {
    @Test
    void recursivelyActivatesEntriesFromActivatedLoreContent() {
        ImportedLorebookEntry bessie = entry("bessie", "Bessie is friends with Rufus.", 20, new LorebookRecursionOptions(
                true, false, false, false));
        ImportedLorebookEntry rufus = entry("rufus", "Rufus is a dog.", 10, LorebookRecursionOptions.DEFAULT);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                true,
                List.of(bessie, rufus));

        List<ImportedLorebookEntry> selected = ImportedLorebookEvaluator.evaluate(book, List.of("Tell me about Bessie."));

        assertEquals(List.of(bessie, rufus), selected);
    }

    @Test
    void limitsRecursiveActivationToConfiguredScanSweeps() {
        ImportedLorebookEntry initial = entry(
                "bessie", "Bessie knows Rufus.", 20, new LorebookRecursionOptions(true, false, false, false));
        ImportedLorebookEntry recursive = entry("rufus", "Rufus is a dog.", 10, LorebookRecursionOptions.DEFAULT);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                true,
                1,
                List.of(initial, recursive));

        assertEquals(List.of(initial), ImportedLorebookEvaluator.evaluate(book, List.of("Bessie arrived.")));
    }

    @Test
    void excludesAZeroProbabilityActivation() {
        ImportedLorebookEntry entry = entry(
                "elder-god", "The Elder God stirs.", 20, LorebookRecursionOptions.DEFAULT,
                new LorebookActivationState(0, 0, 0, 0, "", 1));
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));

        assertEquals(List.of(), ImportedLorebookEvaluator.evaluate(book, List.of("The elder-god is named.")));
    }

    @Test
    void admitsProbabilityEntriesWhenTheClientLocalRollFallsWithinTheirThreshold() {
        ImportedLorebookEntry entry = entry(
                "elder-god", "The Elder God stirs.", 20, LorebookRecursionOptions.DEFAULT,
                new LorebookActivationState(50, 0, 0, 0, "", 1));
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));

        assertEquals(
                List.of(entry),
                ImportedLorebookEvaluator.evaluate(book, List.of("The elder-god is named."), fixedRoll(0)));
    }

    @Test
    void selectsOneWeightedCandidatePerInclusionGroup() {
        ImportedLorebookEntry first = entry(
                "sunny", "Sunny weather.", 30, LorebookRecursionOptions.DEFAULT,
                new LorebookActivationState(100, 0, 0, 0, "weather", 1));
        ImportedLorebookEntry second = entry(
                "rainy", "Rainy weather.", 20, LorebookRecursionOptions.DEFAULT,
                new LorebookActivationState(100, 0, 0, 0, "weather", 9));
        ImportedLorebookEntry independent = entry("storm", "Storm warning.", 10, LorebookRecursionOptions.DEFAULT);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(first, second, independent));

        assertEquals(
                List.of(first, independent),
                ImportedLorebookEvaluator.evaluate(book, List.of("sunny rainy storm"), fixedRoll(0)));
    }

    @Test
    void keepsAStickyEntryActiveForItsConfiguredPrivateConversationMessages() {
        ImportedLorebookEntry entry = entry(
                "lantern", "The lantern remains lit.", 20, LorebookRecursionOptions.DEFAULT,
                new LorebookActivationState(100, 2, 0, 0, "", 1));
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
        ImportedLorebookEvaluator evaluator = new ImportedLorebookEvaluator(fixedRoll(0));

        assertEquals(List.of(entry), evaluator.evaluate(book, List.of("A lantern is here."), 1));
        assertEquals(List.of(entry), evaluator.evaluate(book, List.of("No keyword this time."), 3));
    }

    @Test
    void doesNotTreatStatelessDefaultEvaluationAsAnInfinitePrivateTranscript() {
        assertEquals(List.of(), ImportedLorebookEvaluator.evaluate(delayedBook(), List.of("A lantern is here.")));
    }

    @Test
    void doesNotTreatDeterministicStatelessEvaluationAsAnInfinitePrivateTranscript() {
        assertEquals(List.of(), ImportedLorebookEvaluator.evaluate(delayedBook(), List.of("A lantern is here."), fixedRoll(0)));
    }

    private static ImportedLorebook delayedBook() {
        ImportedLorebookEntry entry = entry(
                "lantern", "The lantern remains lit.", 20, LorebookRecursionOptions.DEFAULT,
                new LorebookActivationState(100, 0, 0, 2, "", 1));
        return new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
    }

    private static RandomGenerator fixedRoll(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }

    private static ImportedLorebookEntry entry(
            String key, String content, int order, LorebookRecursionOptions recursion) {
        return entry(key, content, order, recursion, LorebookActivationState.DEFAULT);
    }

    private static ImportedLorebookEntry entry(
            String key,
            String content,
            int order,
            LorebookRecursionOptions recursion,
            LorebookActivationState activationState) {
        return new ImportedLorebookEntry(
                key,
                0,
                List.of(key),
                List.of(),
                content,
                order,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                activationState,
                recursion);
    }
}
