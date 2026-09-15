package dev.lohrel.plasticmemories.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class NpcProfileTest {
    @Test
    void preservesAllCharacterCardFields() {
        NpcProfile profile = NpcProfile.create("Description", "Personality", "Appearance", "Backstory");

        assertEquals("Description", profile.description());
        assertEquals("Personality", profile.personality());
        assertEquals("Appearance", profile.appearance());
        assertEquals("Backstory", profile.backstory());
    }

    @Test
    void rejectsFieldsBeyondTheirPacketAndPersistenceBounds() {
        assertThrows(IllegalArgumentException.class, () -> NpcProfile.create(
                "x".repeat(NpcProfile.MAX_DESCRIPTION_LENGTH + 1), "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> NpcProfile.create(
                "", "x".repeat(NpcProfile.MAX_PERSONALITY_LENGTH + 1), "", ""));
        assertThrows(IllegalArgumentException.class, () -> NpcProfile.create(
                "", "", "x".repeat(NpcProfile.MAX_APPEARANCE_LENGTH + 1), ""));
        assertThrows(IllegalArgumentException.class, () -> NpcProfile.create(
                "", "", "", "x".repeat(NpcProfile.MAX_BACKSTORY_LENGTH + 1)));
    }
}
