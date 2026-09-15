package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NpcProfileRegistryTest {
    @Test
    void storesProfilesByStableNpcId() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000456");
        NpcProfileRegistry profiles = new NpcProfileRegistry();
        profiles.put(first, NpcProfile.create("First", "", "", ""));
        profiles.put(second, NpcProfile.create("Second", "", "", ""));

        assertEquals("First", profiles.get(first).description());
        assertEquals("Second", profiles.get(second).description());
        assertEquals(NpcProfile.empty(), profiles.get(UUID.randomUUID()));
    }
}
