package dev.lohrel.plasticmemories.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NpcTargetResolverTest {
    @Test
    void rejectsDuplicateNearbyNamesAsAmbiguous() {
        var candidates = List.of(
                new NpcCandidate(UUID.randomUUID(), "John", "mca", 9.0),
                new NpcCandidate(UUID.randomUUID(), "John", "mca", 16.0));

        NpcTargetResolution resolution = NpcTargetResolver.resolve("John", candidates, 32.0);

        assertEquals(TargetResolutionStatus.AMBIGUOUS, resolution.status());
        assertTrue(resolution.target().isEmpty());
    }

    @Test
    void reportsNotFoundInsteadOfThrowingWhenNoNpcMatches() {
        NpcTargetResolution resolution = NpcTargetResolver.resolve("John", List.of(), 32.0);

        assertEquals(TargetResolutionStatus.NOT_FOUND, resolution.status());
        assertTrue(resolution.target().isEmpty());
    }

    @Test
    void resolvesOneNearbyMcaNpcByExactNameIgnoringCase() {
        UUID johnId = UUID.randomUUID();
        var candidates = List.of(new NpcCandidate(johnId, "John", "mca", 25.0));

        NpcTargetResolution resolution = NpcTargetResolver.resolve("john", candidates, 32.0);

        assertEquals(TargetResolutionStatus.FOUND, resolution.status());
        assertTrue(resolution.target().isPresent());
        assertEquals(johnId, resolution.target().orElseThrow().npcId());
    }
}
