package dev.lohrel.plasticmemories.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PendingNpcRequestsTest {
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void completesOnlyTheMatchingNpcAndRequest() {
        PendingNpcRequests<String> requests = new PendingNpcRequests<>();
        var pending = requests.expect(NPC, 7L);

        requests.complete(UUID.randomUUID(), 7L, "wrong npc");
        requests.complete(NPC, 8L, "wrong request");
        assertFalse(pending.isDone());

        requests.complete(NPC, 7L, "answer");
        assertEquals("answer", pending.join());
    }

    @Test
    void aSecondAnswerIsIgnored() {
        PendingNpcRequests<String> requests = new PendingNpcRequests<>();
        var pending = requests.expect(NPC, 7L);

        requests.complete(NPC, 7L, "first");
        requests.complete(NPC, 7L, "second");

        assertEquals("first", pending.join());
    }

    @Test
    void rejectsInvalidAndDuplicateRequestIds() {
        PendingNpcRequests<String> requests = new PendingNpcRequests<>();
        requests.expect(NPC, 7L);

        assertTrue(requests.expect(NPC, 0L).isCompletedExceptionally());
        assertTrue(requests.expect(NPC, 7L).isCompletedExceptionally());
    }

    @Test
    void clearFailsEverythingStillWaiting() {
        PendingNpcRequests<String> requests = new PendingNpcRequests<>();
        var pending = requests.expect(NPC, 7L);

        requests.clear();

        assertTrue(pending.isCompletedExceptionally());
    }
}
