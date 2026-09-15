package dev.lohrel.plasticmemories.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SkillRequestGateTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void acceptsOneValidCookRequest() {
        SkillRequestGate gate = new SkillRequestGate(40, 32.0);

        assertEquals(SkillRequestDecision.ACCEPTED,
                gate.evaluate(PLAYER, NPC, 10L, 100L, validContext()));
    }

    @Test
    void rejectsReplayWithoutStartingAnotherCooldown() {
        SkillRequestGate gate = new SkillRequestGate(40, 32.0);
        gate.evaluate(PLAYER, NPC, 10L, 100L, validContext());

        assertEquals(SkillRequestDecision.REPLAYED,
                gate.evaluate(PLAYER, NPC, 10L, 200L, validContext()));
    }

    @Test
    void replayCannotBeEvictedByNewRequestIds() {
        SkillRequestGate gate = new SkillRequestGate(0, 32.0);
        gate.evaluate(PLAYER, NPC, 1L, 100L, validContext());
        for (long requestId = 2; requestId <= 200; requestId++) {
            gate.evaluate(PLAYER, NPC, requestId, requestId + 100, validContext());
        }

        assertEquals(SkillRequestDecision.REPLAYED,
                gate.evaluate(PLAYER, NPC, 1L, 1_000L, validContext()));
    }

    @Test
    void forgettingDisconnectedPlayerAllowsANewSequence() {
        SkillRequestGate gate = new SkillRequestGate(0, 32.0);
        gate.evaluate(PLAYER, NPC, 10L, 100L, validContext());
        gate.forgetPlayer(PLAYER);

        assertEquals(SkillRequestDecision.ACCEPTED,
                gate.evaluate(PLAYER, UUID.randomUUID(), 1L, 200L, validContext()));
    }

    @Test
    void rejectsPlayerAndNpcCooldowns() {
        SkillRequestGate gate = new SkillRequestGate(40, 32.0);
        gate.evaluate(PLAYER, NPC, 10L, 100L, validContext());

        assertEquals(SkillRequestDecision.RATE_LIMITED,
                gate.evaluate(PLAYER, UUID.randomUUID(), 11L, 120L, validContext()));
        assertEquals(SkillRequestDecision.NPC_BUSY,
                gate.evaluate(UUID.randomUUID(), NPC, 12L, 120L, validContext()));
    }

    @Test
    void rejectsUnsupportedDeadDistantAndBusyTargets() {
        SkillRequestGate gate = new SkillRequestGate(40, 32.0);

        assertEquals(SkillRequestDecision.UNSUPPORTED_NPC,
                gate.evaluate(PLAYER, NPC, 1L, 100L, new SkillRequestContext(false, true, false, 1.0)));
        assertEquals(SkillRequestDecision.INVALID_NPC,
                gate.evaluate(PLAYER, NPC, 2L, 100L, new SkillRequestContext(true, false, false, 1.0)));
        assertEquals(SkillRequestDecision.OUT_OF_RANGE,
                gate.evaluate(PLAYER, NPC, 3L, 100L, new SkillRequestContext(true, true, false, 32.01 * 32.01)));
        assertEquals(SkillRequestDecision.NPC_BUSY,
                gate.evaluate(PLAYER, NPC, 4L, 100L, new SkillRequestContext(true, true, true, 1.0)));
    }

    private static SkillRequestContext validContext() {
        return new SkillRequestContext(true, true, false, 1.0);
    }
}
