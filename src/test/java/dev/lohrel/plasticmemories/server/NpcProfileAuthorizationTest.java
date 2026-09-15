package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NpcProfileAuthorizationTest {
    @Test
    void allowsOnlyOperatorsOrSingleplayerOwnersToEdit() {
        assertTrue(NpcProfileAuthorization.canEdit(true, false));
        assertTrue(NpcProfileAuthorization.canEdit(false, true));
        assertFalse(NpcProfileAuthorization.canEdit(false, false));
    }
}
