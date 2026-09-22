package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerRequestLimiterTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void acceptsIncreasingIdsSpacedByTheCooldown() {
        PlayerRequestLimiter limiter = new PlayerRequestLimiter(20);

        assertEquals(PlayerRequestLimiter.Admission.ACCEPTED, limiter.admit(PLAYER, 1, 100));
        assertEquals(PlayerRequestLimiter.Admission.RATE_LIMITED, limiter.admit(PLAYER, 2, 119));
        assertEquals(PlayerRequestLimiter.Admission.ACCEPTED, limiter.admit(PLAYER, 2, 120));
    }

    @Test
    void rejectsRepeatedOrOlderIdsEvenAfterTheCooldown() {
        PlayerRequestLimiter limiter = new PlayerRequestLimiter(1);
        limiter.admit(PLAYER, 5, 100);

        assertEquals(PlayerRequestLimiter.Admission.REPLAYED, limiter.admit(PLAYER, 5, 200));
        assertEquals(PlayerRequestLimiter.Admission.REPLAYED, limiter.admit(PLAYER, 4, 200));
    }

    @Test
    void rejectedRequestsDoNotConsumeTheId() {
        PlayerRequestLimiter limiter = new PlayerRequestLimiter(20);
        limiter.admit(PLAYER, 1, 100);

        assertEquals(PlayerRequestLimiter.Admission.RATE_LIMITED, limiter.admit(PLAYER, 2, 101));
        assertEquals(PlayerRequestLimiter.Admission.ACCEPTED, limiter.admit(PLAYER, 2, 120));
    }

    @Test
    void playersAreLimitedIndependently() {
        PlayerRequestLimiter limiter = new PlayerRequestLimiter(20);
        limiter.admit(PLAYER, 1, 100);

        assertEquals(PlayerRequestLimiter.Admission.ACCEPTED, limiter.admit(UUID.randomUUID(), 1, 100));
    }

    @Test
    void allowPacketOnlyChecksSpacing() {
        PlayerRequestLimiter limiter = new PlayerRequestLimiter(1);

        assertTrue(limiter.allowPacket(PLAYER, 100));
        assertFalse(limiter.allowPacket(PLAYER, 100));
        assertTrue(limiter.allowPacket(PLAYER, 101));
    }

    @Test
    void forgettingAPlayerResetsTheirState() {
        PlayerRequestLimiter limiter = new PlayerRequestLimiter(20);
        limiter.admit(PLAYER, 5, 100);

        limiter.forget(PLAYER);

        assertEquals(PlayerRequestLimiter.Admission.ACCEPTED, limiter.admit(PLAYER, 1, 100));
    }
}
