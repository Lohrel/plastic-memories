package dev.lohrel.plasticmemories.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.lorebook.ClientLorebookLibraryStore;
import dev.lohrel.plasticmemories.lorebook.ClientLorebookRequestContextLoader;
import dev.lohrel.plasticmemories.lorebook.ImportedLorebookEntry;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import dev.lohrel.plasticmemories.memory.ConversationMemory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CharacterCardsTest {
    private static final LocalLorebookBindingKey NPC = new LocalLorebookBindingKey(
            "singleplayer:/worlds/Test", UUID.randomUUID(), UUID.randomUUID());

    @TempDir
    Path directory;

    @Test
    void boundCardIsReadFromTheFolder() throws Exception {
        CharacterCards cards = cards();
        TestCards.writePng(directory.resolve("cards").resolve("seraphina.png"), "Seraphina");

        cards.bind(NPC, "seraphina.png");

        assertEquals("Seraphina", cards.boundCard(NPC).orElseThrow().name());
    }

    @Test
    void deletingTheFileLeavesTheNpcWithoutACard() throws Exception {
        CharacterCards cards = cards();
        Path file = TestCards.writePng(directory.resolve("cards").resolve("seraphina.png"), "Seraphina");
        cards.bind(NPC, "seraphina.png");

        Files.delete(file);

        assertTrue(cards.boundCard(NPC).isEmpty());
    }

    @Test
    void theBoundCardAndItsBookReachThePrompt() throws Exception {
        CharacterCards cards = cards();
        TestCards.writeJson(directory.resolve("cards").resolve("warden.json"), "Warden");
        cards.bind(NPC, "warden.json");
        ClientLorebookRequestContextLoader loader = new ClientLorebookRequestContextLoader(
                new ClientLorebookLibraryStore(directory.resolve("library")), cards::boundCard);

        var context = loader.load(NPC, ConversationMemory.empty(), "Who watches the gate?");

        assertEquals("Warden", context.card().orElseThrow().name());
        assertEquals(java.util.List.of("Warden guards the gate."),
                context.loreEntries().stream().map(ImportedLorebookEntry::content).toList());
    }

    @Test
    void editsApplyOnlyToThatNpcAndLeaveTheFileAlone() throws Exception {
        CharacterCards cards = cards();
        Path file = TestCards.writeJson(directory.resolve("cards").resolve("warden.json"), "Warden");
        String original = Files.readString(file);
        LocalLorebookBindingKey other = new LocalLorebookBindingKey(NPC.worldIdentity(), NPC.playerId(), UUID.randomUUID());
        cards.bind(NPC, "warden.json");
        cards.bind(other, "warden.json");

        cards.saveEdits(NPC, java.util.Map.of(CardField.DESCRIPTION, "Edited in game."));

        assertEquals("Edited in game.", cards.boundCard(NPC).orElseThrow().description());
        assertEquals("About Warden.", cards.boundCard(other).orElseThrow().description());
        assertEquals(original, Files.readString(file));
        assertEquals(java.util.Set.of(CardField.DESCRIPTION), cards.editableCard(NPC).orElseThrow().edited());
    }

    @Test
    void unchangedFieldsFollowTheFile() throws Exception {
        CharacterCards cards = cards();
        Path file = TestCards.writeJson(directory.resolve("cards").resolve("warden.json"), "Warden");
        cards.bind(NPC, "warden.json");
        cards.saveEdits(NPC, java.util.Map.of(CardField.PERSONALITY, "Grumpy."));

        Files.writeString(file, TestCards.cardJson("Warden").replace("About Warden.", "Updated in the file."));
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(java.time.Instant.now().plusSeconds(5)));

        assertEquals("Updated in the file.", cards.boundCard(NPC).orElseThrow().description());
        assertEquals("Grumpy.", cards.boundCard(NPC).orElseThrow().personality());
    }

    @Test
    void savingTheFileValueIsNotAnEdit() throws Exception {
        CharacterCards cards = cards();
        TestCards.writeJson(directory.resolve("cards").resolve("warden.json"), "Warden");
        cards.bind(NPC, "warden.json");

        cards.saveEdits(NPC, java.util.Map.of(CardField.DESCRIPTION, "About Warden.", CardField.SCENARIO, ""));

        assertTrue(cards.editableCard(NPC).orElseThrow().edited().isEmpty());
    }

    @Test
    void resetGoesBackToTheFile() throws Exception {
        CharacterCards cards = cards();
        TestCards.writeJson(directory.resolve("cards").resolve("warden.json"), "Warden");
        cards.bind(NPC, "warden.json");
        cards.saveEdits(NPC, java.util.Map.of(CardField.DESCRIPTION, "Edited."));

        cards.resetEdits(NPC);

        assertEquals("About Warden.", cards.boundCard(NPC).orElseThrow().description());
    }

    @Test
    void pickingAnotherCardDropsTheEditsButPickingTheSameOneKeepsThem() throws Exception {
        CharacterCards cards = cards();
        TestCards.writeJson(directory.resolve("cards").resolve("warden.json"), "Warden");
        TestCards.writeJson(directory.resolve("cards").resolve("guard.json"), "Guard");
        cards.bind(NPC, "warden.json");
        cards.saveEdits(NPC, java.util.Map.of(CardField.DESCRIPTION, "Edited."));

        cards.bind(NPC, "warden.json");
        assertEquals("Edited.", cards.boundCard(NPC).orElseThrow().description());

        cards.bind(NPC, "guard.json");
        cards.bind(NPC, "warden.json");
        assertEquals("About Warden.", cards.boundCard(NPC).orElseThrow().description());
    }

    private CharacterCards cards() throws Exception {
        Files.createDirectories(directory.resolve("cards"));
        return new CharacterCards(
                new CharacterCardFolder(directory.resolve("cards")),
                new CardBindingStore(directory.resolve("card-bindings.json")));
    }
}
