package dev.lohrel.plasticmemories.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SkillResultMessagesTest {
    @Test
    void explainsWhenNoSupportedContainerWasFound() {
        assertEquals(
                "[Plastic Memories] COOK failed: no accessible food container was found nearby.",
                SkillResultMessages.message(SkillResultCode.NO_CONTAINER));
    }

    @Test
    void explainsWhenNpcCannotCarryRetrievedFood() {
        assertEquals(
                "[Plastic Memories] COOK failed: the NPC has no inventory space to carry food.",
                SkillResultMessages.message(SkillResultCode.NPC_INVENTORY_FULL));
    }
}
