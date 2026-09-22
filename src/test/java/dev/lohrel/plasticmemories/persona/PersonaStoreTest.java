package dev.lohrel.plasticmemories.persona;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersonaStoreTest {
    private static final LocalLorebookBindingKey KING = new LocalLorebookBindingKey(
            "singleplayer:/worlds/Test",
            UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final LocalLorebookBindingKey BAKER =
            new LocalLorebookBindingKey(KING.worldIdentity(), KING.playerId(), UUID.randomUUID());

    @TempDir
    Path directory;

    @Test
    void savesEditsAndListsPersonasInCreationOrder() throws Exception {
        PersonaStore store = store();
        Persona alex = store.create("Alex", "A young farmer.");
        store.create("The Knight", "Sworn to the crown.");

        store.update(new Persona(alex.id(), "Alex", "A young farmer from the east."));

        assertEquals(List.of("Alex", "The Knight"), store().list().stream().map(Persona::name).toList());
        assertEquals("A young farmer from the east.", store().list().getFirst().description());
    }

    @Test
    void lockedPersonaWinsOverTheActiveOne() throws Exception {
        PersonaStore store = store();
        Persona alex = store.create("Alex", "");
        Persona knight = store.create("The Knight", "");
        store.setActive(alex.id());

        store.lock(KING, knight.id());

        assertEquals(Optional.of(knight), store().personaFor(KING));
        assertEquals(Optional.of(alex), store().personaFor(BAKER));
        store.unlock(KING);
        assertEquals(Optional.of(alex), store().personaFor(KING));
    }

    @Test
    void cyclingActivatesTheNextPersonaAndWrapsAround() throws Exception {
        PersonaStore store = store();
        Persona alex = store.create("Alex", "");
        Persona knight = store.create("The Knight", "");

        assertEquals(Optional.of(alex), store.cycleActive());
        assertEquals(Optional.of(knight), store.cycleActive());
        assertEquals(Optional.of(alex), store.cycleActive());
        assertEquals(Optional.of(alex), store().active());
    }

    @Test
    void cyclingWithNoPersonasDoesNothing() throws Exception {
        assertTrue(store().cycleActive().isEmpty());
    }

    @Test
    void deletingAPersonaClearsItsActivationAndLocks() throws Exception {
        PersonaStore store = store();
        Persona knight = store.create("The Knight", "");
        store.setActive(knight.id());
        store.lock(KING, knight.id());

        store.delete(knight.id());

        assertTrue(store().active().isEmpty());
        assertTrue(store().personaFor(KING).isEmpty());
        assertTrue(store().list().isEmpty());
    }

    @Test
    void namesMustNotBeBlankOrTooLong() {
        assertThrows(IllegalArgumentException.class, () -> store().create(" ", ""));
        assertThrows(IllegalArgumentException.class, () -> store().create("x".repeat(Persona.MAX_NAME_LENGTH + 1), ""));
        assertThrows(IllegalArgumentException.class,
                () -> store().create("Alex", "x".repeat(Persona.MAX_DESCRIPTION_LENGTH + 1)));
    }

    // Frozen copy of the v1 file format. Never edit the fixture: if this breaks, add a migration.
    @Test
    void version1FilesStillLoad() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/compat/personas_v1.json")) {
            Files.write(directory.resolve("personas.json"), fixture.readAllBytes());
        }
        PersonaStore store = store();

        assertEquals(List.of("Alex", "The Knight"), store.list().stream().map(Persona::name).toList());
        assertEquals("Alex", store.active().orElseThrow().name());
        assertEquals("The Knight", store.personaFor(KING).orElseThrow().name());
    }

    @Test
    void unknownFieldsSurviveASave() throws Exception {
        Files.writeString(directory.resolve("personas.json"),
                "{\"version\":1,\"personas\":[],\"npcLocks\":[],\"fromTheFuture\":1}");

        store().create("Alex", "");

        assertTrue(Files.readString(directory.resolve("personas.json")).contains("\"fromTheFuture\":1"));
    }

    private PersonaStore store() {
        return new PersonaStore(directory.resolve("personas.json"));
    }

    @Test
    void personasThisBuildCannotReadAreKeptOnSave() throws Exception {
        String tooLong = "x".repeat(Persona.MAX_DESCRIPTION_LENGTH + 1);
        Files.writeString(directory.resolve("personas.json"), "{\"version\":1,\"personas\":[{\"id\":"
                + "\"33333333-3333-3333-3333-333333333333\",\"name\":\"Long\",\"description\":\"" + tooLong
                + "\"}],\"npcLocks\":[]}");

        store().create("Alex", "");

        assertEquals(List.of("Alex"), store().list().stream().map(Persona::name).toList());
        assertTrue(Files.readString(directory.resolve("personas.json")).contains(tooLong));
    }
}
