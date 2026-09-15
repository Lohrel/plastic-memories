package dev.lohrel.plasticmemories.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NpcProfileClientInboxTest {
    @Test
    void ignoresResponsesForAnotherNpcWithTheSameRequestId() {
        UUID expectedNpc = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UUID wrongNpc = UUID.fromString("00000000-0000-0000-0000-000000000456");
        NpcProfileClientInbox inbox = new NpcProfileClientInbox();
        var response = inbox.expect(expectedNpc, 7L);

        inbox.complete(wrongNpc, 7L, NpcProfileResultCode.SUCCESS, NpcProfile.empty(), true);
        assertFalse(response.isDone());

        inbox.complete(expectedNpc, 7L, NpcProfileResultCode.SUCCESS, NpcProfile.empty(), true);
        assertEquals(NpcProfileResultCode.SUCCESS, response.join().result());
    }
}
