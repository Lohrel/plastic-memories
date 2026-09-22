package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
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

    @Test
    void matchesExplicitlyActiveImportedLoreAgainstPrivateMemoryAndCurrentMessage() {
        ImportedLorebookEntry entry = new ImportedLorebookEntry(
                "sundial",
                0,
                List.of("sundial"),
                List.of(),
                "The sundial is ancient.",
                10,
                true,
                false,
                false,
                SecondaryKeyLogic.AND_ANY,
                new LorebookMatchOptions(false, false, false, 3),
                LorebookInsertion.DEFAULT,
                LorebookActivationState.DEFAULT,
                LorebookRecursionOptions.DEFAULT);
        ImportedLorebook book = new ImportedLorebook(
                LorebookSourceFormat.CLASSIC_WORLD_INFO,
                CompatibilityProfile.SILLY_TAVERN,
                0,
                3,
                false,
                List.of(entry));
        ConversationMemory memory = ConversationMemory.empty().append("Tell me about time.", "The sundial casts no shadow.");

        ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                new ClientLorebookContext(java.util.Optional.empty(), List.of(book)),
                memory,
                "What do you remember?",
                new ImportedLorebookEvaluator(new java.util.Random(0)),
                memory.turns().size() + 1);

        assertEquals(List.of(entry), prompt.loreEntries());
    }

    @Test
    void entriesFromAllLorebooksAreOrderedByPriorityTogether() {
        ImportedLorebook first = book(entry("low", "alpha", 10, LorebookInsertion.DEFAULT, ""));
        ImportedLorebook second = book(entry("high", "alpha", 1000, LorebookInsertion.DEFAULT, ""));

        ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                new ClientLorebookContext(Optional.empty(), List.of(first, second)), List.of("alpha"));

        assertEquals(List.of("high", "low"), prompt.loreEntries().stream().map(ImportedLorebookEntry::id).toList());
    }

    @Test
    void skippedEntriesDoNotWinInclusionGroups() {
        // SillyTavern position 7 is an outlet, which we skip; it must not beat the usable entry in its group.
        ImportedLorebookEntry outlet = entry("outlet", "alpha", 100, new LorebookInsertion(7, 0, LorebookPromptRole.SYSTEM, "x"), "g");
        ImportedLorebookEntry usable = entry("usable", "alpha", 1, LorebookInsertion.DEFAULT, "g");

        for (int attempt = 0; attempt < 20; attempt++) {
            ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                    new ClientLorebookContext(Optional.empty(), List.of(book(outlet, usable))), List.of("alpha"));
            assertEquals(List.of("usable"), prompt.loreEntries().stream().map(ImportedLorebookEntry::id).toList());
        }
    }

    private static ImportedLorebookEntry entry(String id, String key, int order, LorebookInsertion insertion, String group) {
        return new ImportedLorebookEntry(
                id, 0, List.of(key), List.of(), "Content of " + id + ".", order, true, false, false,
                SecondaryKeyLogic.AND_ANY, new LorebookMatchOptions(false, false, false, 3), insertion,
                new LorebookActivationState(100, 0, 0, 0, group, 1), LorebookRecursionOptions.DEFAULT);
    }

    @Test
    void entriesCutByTheBudgetDoNotStartTheirCooldown() {
        java.util.ArrayList<ImportedLorebookEntry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < LorebookLimits.MAX_PROMPT_LORE_ENTRIES; index++) {
            entries.add(entry("filler-" + index, "alpha", 100 + index, LorebookInsertion.DEFAULT, ""));
        }
        ImportedLorebookEntry cooled = new ImportedLorebookEntry(
                "cooled", 0, List.of("alpha", "beta"), List.of(), "Cooled lore.", 1, true, false, false,
                SecondaryKeyLogic.AND_ANY, new LorebookMatchOptions(false, false, false, 3), LorebookInsertion.DEFAULT,
                new LorebookActivationState(100, 0, 5, 0, "", 1), LorebookRecursionOptions.DEFAULT);
        entries.add(cooled);
        ClientLorebookContext context = new ClientLorebookContext(Optional.empty(), List.of(book(entries.toArray(ImportedLorebookEntry[]::new))));
        ImportedLorebookEvaluator evaluator = new ImportedLorebookEvaluator(new java.util.Random(0));

        ImportedPromptContext first = ImportedPromptContextResolver.resolve(context, List.of("alpha"), evaluator, 1);
        ImportedPromptContext second = ImportedPromptContextResolver.resolve(context, List.of("beta"), evaluator, 2);

        assertEquals(LorebookLimits.MAX_PROMPT_LORE_ENTRIES, first.lore().size());
        assertFalse(first.loreEntries().contains(cooled), "lowest priority entry is over the budget");
        assertEquals(List.of(cooled), second.loreEntries(), "it was never sent, so no cooldown");
    }

    @Test
    void budgetKeepsTheHighestPriorityEntries() {
        java.util.ArrayList<ImportedLorebookEntry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < 40; index++) {
            entries.add(entry("entry-" + index, "alpha", index, LorebookInsertion.DEFAULT, ""));
        }

        ImportedPromptContext prompt = ImportedPromptContextResolver.resolve(
                new ClientLorebookContext(Optional.empty(), List.of(book(entries.toArray(ImportedLorebookEntry[]::new)))),
                List.of("alpha"));

        assertEquals(LorebookLimits.MAX_PROMPT_LORE_ENTRIES, prompt.lore().size());
        assertEquals("entry-39", prompt.loreEntries().getFirst().id());
        assertFalse(prompt.loreEntries().stream().anyMatch(entry -> entry.id().equals("entry-0")));
    }
}
