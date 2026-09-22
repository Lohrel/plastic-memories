package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CompatibilityProfileTest {
    @Test
    void exposesOnlySillyTavernAndMarinaraProfiles() {
        assertEquals(List.of(CompatibilityProfile.SILLY_TAVERN, CompatibilityProfile.MARINARA),
                List.of(CompatibilityProfile.values()));
    }

    @Test
    void mapsRemovedLegacyProfilesToSillyTavernDuringPersistenceMigration() {
        assertEquals(CompatibilityProfile.SILLY_TAVERN, CompatibilityProfile.fromPersistedName("CHUB"));
        assertEquals(CompatibilityProfile.SILLY_TAVERN, CompatibilityProfile.fromPersistedName("PLASTIC_MEMORIES"));
    }
}
