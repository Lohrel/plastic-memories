package dev.lohrel.plasticmemories.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.lorebook.ImportedCharacterCard;
import dev.lohrel.plasticmemories.lorebook.ImportedLorebookEntry;
import dev.lohrel.plasticmemories.lorebook.ImportedPromptContext;
import dev.lohrel.plasticmemories.lorebook.LorebookActivationState;
import dev.lohrel.plasticmemories.lorebook.LorebookInsertion;
import dev.lohrel.plasticmemories.lorebook.LorebookMatchOptions;
import dev.lohrel.plasticmemories.lorebook.LorebookPlacement;
import dev.lohrel.plasticmemories.lorebook.LorebookRecursionOptions;
import dev.lohrel.plasticmemories.lorebook.SecondaryKeyLogic;
import dev.lohrel.plasticmemories.memory.ConversationMemory;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PromptPersonaTest {
    @Test
    void macrosAreReplacedCaseInsensitivelyIncludingLegacyForms() {
        assertEquals("Alex greets John. Alex bows to John.",
                PromptMacros.apply("{{user}} greets {{char}}. <USER> bows to {{Char}}.", "Alex", "John"));
        assertEquals("John waves.", PromptMacros.apply("<BOT> waves.", "Alex", "John"));
    }

    @Test
    void personaDescriptionIsIncludedAndMacrosAreReplacedEverywhere() {
        ImportedCharacterCard card = new ImportedCharacterCard(
                "Warden", "{{char}} guards the gate for {{user}}.", "", "", "", List.of(), "",
                "", "", List.of(), "", "", "", "", Optional.empty());
        ImportedLorebookEntry lore = new ImportedLorebookEntry(
                "gate", 0, List.of("gate"), List.of(), "{{user}} once opened the gate.", 10, true, false, false,
                SecondaryKeyLogic.AND_ANY, new LorebookMatchOptions(false, false, false, 3), LorebookInsertion.DEFAULT,
                LorebookActivationState.DEFAULT, LorebookRecursionOptions.DEFAULT);

        String system = new PromptBuilder().build(
                ProviderSettings.create("https://example.com/v1", "m", "k"),
                "John",
                ConversationMemory.empty(),
                "Hello",
                CookAvailability.AVAILABLE,
                NpcProfile.create("{{char}} knows {{user}}.", "", "", ""),
                new ImportedPromptContext(Optional.of(card), List.of(
                        new ImportedPromptContext.PlacedLoreEntry(lore, LorebookPlacement.BEFORE_CHARACTER))),
                new PromptPersona("Alex", "A young farmer from the east.")).messages().getFirst().content();

        assertTrue(system.contains("John guards the gate for Alex."));
        assertTrue(system.contains("Alex once opened the gate."));
        assertTrue(system.contains("John knows Alex."));
        assertTrue(system.contains("A young farmer from the east."));
        assertFalse(system.contains("{{"));
    }

    @Test
    void withoutADescriptionOnlyTheNameIsGiven() {
        String system = new PromptBuilder().build(
                ProviderSettings.create("https://example.com/v1", "m", "k"),
                "John", ConversationMemory.empty(), "Hello", CookAvailability.AVAILABLE, NpcProfile.empty(),
                ImportedPromptContext.empty(), new PromptPersona("Steve", "")).messages().getFirst().content();

        assertTrue(system.contains("Steve"));
        assertFalse(system.contains("About Steve"));
    }

    @Test
    void namesAreInsertedInOnePassSoTheyAreNotExpandedAgain() {
        assertEquals("Hi, <BOT> fan! I am John.", PromptMacros.apply("Hi, {{user}}! I am {{char}}.", "<BOT> fan", "John"));
    }
}
