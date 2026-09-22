package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ImportedLorebookPromptContextFactoryTest {
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

        ImportedPromptContext prompt = ImportedLorebookPromptContextFactory.create(
                new ClientLorebookContext(java.util.Optional.empty(), List.of(book)), memory, "What do you remember?");

        assertEquals(List.of(entry), prompt.loreEntries());
    }
}
