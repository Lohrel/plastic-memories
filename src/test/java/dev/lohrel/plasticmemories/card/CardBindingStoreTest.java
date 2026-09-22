package dev.lohrel.plasticmemories.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CardBindingStoreTest {
    private static final LocalLorebookBindingKey MILLER = new LocalLorebookBindingKey(
            "singleplayer:/worlds/Test",
            UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @TempDir
    Path directory;

    @Test
    void bindingsArePerNpcAndSurviveReload() throws Exception {
        Path file = directory.resolve("card-bindings.json");
        LocalLorebookBindingKey baker = new LocalLorebookBindingKey(MILLER.worldIdentity(), MILLER.playerId(), UUID.randomUUID());

        new CardBindingStore(file).bind(MILLER, "seraphina.png");

        CardBindingStore reloaded = new CardBindingStore(file);
        assertEquals(Optional.of("seraphina.png"), reloaded.boundFile(MILLER));
        assertTrue(reloaded.boundFile(baker).isEmpty());

        reloaded.bind(MILLER, "warden.json");
        assertEquals(Optional.of("warden.json"), reloaded.boundFile(MILLER));
        reloaded.unbind(MILLER);
        assertTrue(new CardBindingStore(file).boundFile(MILLER).isEmpty());
    }

    // Frozen copy of the v1 file format. Never edit the fixture: if this breaks, add a migration.
    @Test
    void version1FilesStillLoad() throws Exception {
        Path file = directory.resolve("card-bindings.json");
        try (var fixture = getClass().getResourceAsStream("/compat/card_bindings_v1.json")) {
            Files.write(file, fixture.readAllBytes());
        }

        assertEquals(Optional.of("seraphina.png"), new CardBindingStore(file).boundFile(MILLER));
    }

    @Test
    void unknownFieldsSurviveASave() throws Exception {
        Path file = directory.resolve("card-bindings.json");
        Files.writeString(file, "{\"version\":1,\"bindings\":[],\"fromTheFuture\":1}");

        new CardBindingStore(file).bind(MILLER, "seraphina.png");

        assertTrue(Files.readString(file).contains("\"fromTheFuture\":1"));
    }

    @Test
    void editsSurviveReload() throws Exception {
        Path file = directory.resolve("card-bindings.json");
        CardBindingStore store = new CardBindingStore(file);
        store.bind(MILLER, "seraphina.png");

        store.saveEdits(MILLER, java.util.Map.of(CardField.SCENARIO, "A new scenario."));

        assertEquals(java.util.Map.of(CardField.SCENARIO, "A new scenario."), new CardBindingStore(file).edits(MILLER));
    }
}
