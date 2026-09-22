package dev.lohrel.plasticmemories.card;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CharacterCardFolderTest {
    @TempDir
    Path directory;

    @Test
    void listsCardsByNameAndIgnoresEverythingElse() throws Exception {
        TestCards.writePng(directory.resolve("seraphina.png"), "Seraphina");
        TestCards.writeJson(directory.resolve("warden.json"), "Warden");
        TestCards.writeCharx(directory.resolve("aqua.charx"), "Aqua", TestCards.png(null));
        Files.writeString(directory.resolve("lorebook.json"), "{\"entries\":[{\"keys\":[\"a\"],\"content\":\"Lore.\"}]}");
        Files.writeString(directory.resolve("notes.txt"), "not a card");

        var cards = new CharacterCardFolder(directory).list();

        assertEquals(java.util.List.of("Aqua", "Seraphina", "Warden"), cards.stream().map(card -> card.card().name()).toList());
        assertEquals("seraphina.png", cards.get(1).fileName());
    }

    @Test
    void missingFolderIsEmptyAndNotCreated() {
        Path missing = directory.resolve("character-cards");

        assertTrue(new CharacterCardFolder(missing).list().isEmpty());
        assertTrue(Files.notExists(missing));
    }

    @Test
    void editedFilesAreReadAgain() throws Exception {
        CharacterCardFolder folder = new CharacterCardFolder(directory);
        Path file = TestCards.writeJson(directory.resolve("warden.json"), "Warden");
        assertEquals("Warden", folder.find("warden.json").orElseThrow().card().name());

        TestCards.writeJson(file, "Warden the Second");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));

        assertEquals("Warden the Second", folder.find("warden.json").orElseThrow().card().name());
    }

    @Test
    void findRejectsNamesOutsideTheFolder() throws Exception {
        TestCards.writeJson(directory.resolve("warden.json"), "Warden");
        CharacterCardFolder folder = new CharacterCardFolder(directory.resolve("cards"));

        assertTrue(folder.find("../warden.json").isEmpty());
    }

    @Test
    void portraitComesFromThePngItselfOrTheCharxIcon() throws Exception {
        Path png = TestCards.writePng(directory.resolve("seraphina.png"), "Seraphina");
        byte[] icon = TestCards.png(null);
        TestCards.writeCharx(directory.resolve("aqua.charx"), "Aqua", icon);
        TestCards.writeJson(directory.resolve("warden.json"), "Warden");
        CharacterCardFolder folder = new CharacterCardFolder(directory);

        assertArrayEquals(Files.readAllBytes(png), folder.readPortrait("seraphina.png").orElseThrow());
        assertArrayEquals(icon, folder.readPortrait("aqua.charx").orElseThrow());
        assertTrue(folder.readPortrait("warden.json").isEmpty());
    }
}
