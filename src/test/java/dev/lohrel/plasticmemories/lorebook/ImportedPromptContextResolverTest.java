package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ImportedPromptContextResolverTest {
    @Test
    void keepsTheBoundCardAndOnlyMatchesFromActiveBooksInLibraryOrder() {
        ImportedLorebookEntry cardEntry = entry("card", "moon", 30);
        ImportedLorebookEntry globalEntry = entry("global", "forest", 20);
        ImportedLorebookEntry unmatchedEntry = entry("unmatched", "village", 10);
        ImportedLorebook cardBook = book(cardEntry);
        ImportedLorebook globalBook = book(globalEntry, unmatchedEntry);
        ImportedCharacterCard card = new ImportedCharacterCard(
                "Warden", "", "", "", "", List.of(), "", "", "", List.of(), "", "", "", "", Optional.of(cardBook));
        ClientLorebookContext active = new ClientLorebookContext(Optional.of(card), List.of(cardBook, globalBook));

        ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                active, List.of("The moon rises above the forest."));

        assertEquals(card, prompt.card().orElseThrow());
        assertEquals(List.of(cardEntry, globalEntry), prompt.loreEntries());
    }

    @Test
    void evaluatesRecursiveMatchesBeforeBuildingProviderContext() {
        ImportedLorebookEntry initial = importedEntry(
                "initial", "bessie", "Bessie knows Rufus.", 20, new LorebookRecursionOptions(true, false, false, false));
        ImportedLorebookEntry recursive = importedEntry(
                "recursive", "rufus", "Rufus is a dog.", 10, LorebookRecursionOptions.DEFAULT);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                true,
                List.of(initial, recursive));

        ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                new ClientLorebookContext(Optional.empty(), List.of(book)), List.of("Bessie arrived."));

        assertEquals(List.of(initial, recursive), prompt.loreEntries());
    }

    @Test
    void doesNotActivateDelayedLoreThroughTheStatelessResolverConvenienceMethod() {
        ImportedLorebookEntry delayed = new ImportedLorebookEntry(
                "delayed",
                0,
                List.of("lantern"),
                List.of(),
                "The lantern remains lit.",
                10,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                new LorebookActivationState(100, 0, 0, 2, "", 1),
                LorebookRecursionOptions.DEFAULT);

        ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                new ClientLorebookContext(Optional.empty(), List.of(book(delayed))), List.of("A lantern is here."));

        assertEquals(List.of(), prompt.loreEntries());
    }

    private static ImportedLorebook book(ImportedLorebookEntry... entries) {
        return new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entries));
    }

    private static ImportedLorebookEntry importedEntry(
            String id, String key, String content, int order, LorebookRecursionOptions recursion) {
        return new ImportedLorebookEntry(
                id,
                order,
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
                LorebookActivationState.DEFAULT,
                recursion);
    }

    private static ImportedLorebookEntry entry(String id, String key, int order) {
        return new ImportedLorebookEntry(
                id,
                order,
                List.of(key),
                List.of(),
                id + " content",
                order,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                LorebookActivationState.DEFAULT,
                LorebookRecursionOptions.DEFAULT);
    }
}
