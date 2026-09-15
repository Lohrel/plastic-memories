package dev.lohrel.plasticmemories.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ConversationMemoryTest {
    @Test
    void keepsOnlyTheNewestTwelveConversationTurns() {
        ConversationMemory memory = ConversationMemory.empty();

        for (int index = 0; index < 13; index++) {
            memory = memory.append("player-" + index, "npc-" + index);
        }

        assertEquals(12, memory.turns().size());
        assertEquals("player-1", memory.turns().getFirst().playerMessage());
        assertEquals("npc-12", memory.turns().getLast().npcReply());
    }
}
