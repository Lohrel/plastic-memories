package dev.lohrel.plasticmemories.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NpcCapabilityClientInboxTest {
    @Test
    void completesOnlyMatchingNpcAndRequest() {
        NpcCapabilityClientInbox inbox = new NpcCapabilityClientInbox();
        UUID expectedNpc = UUID.randomUUID();
        var pending = inbox.expect(expectedNpc, 7L);

        inbox.complete(UUID.randomUUID(), 7L, CookAvailability.AVAILABLE);
        assertFalse(pending.isDone());

        inbox.complete(expectedNpc, 7L, CookAvailability.NO_FOOD);
        assertEquals(CookAvailability.NO_FOOD, pending.join());
    }
}
