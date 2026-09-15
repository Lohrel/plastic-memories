package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CookTaskLevelPolicyTest {
    @Test
    void requiresPlayerAndNpcToRemainInOriginLevel() {
        Object origin = new Object();

        assertTrue(CookTaskLevelPolicy.remainsInOrigin(origin, origin, origin));
        assertFalse(CookTaskLevelPolicy.remainsInOrigin(origin, new Object(), origin));
        assertFalse(CookTaskLevelPolicy.remainsInOrigin(origin, origin, new Object()));
    }
}
