package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CookContainerSearchProgressTest {
    @Test
    void examinesEachCandidateForSixtyTicksUntilFoodIsPickedUp() {
        CookContainerSearchProgress progress = new CookContainerSearchProgress(2, 60);

        assertEquals(CookContainerSearchProgress.Phase.MOVING, progress.phase());
        assertEquals(0, progress.currentIndex().orElseThrow());

        progress.arrived(100L);
        assertFalse(progress.readyToInspect(159L));
        assertTrue(progress.readyToInspect(160L));
        progress.inspected(false);

        assertEquals(CookContainerSearchProgress.Phase.MOVING, progress.phase());
        assertEquals(1, progress.currentIndex().orElseThrow());

        progress.arrived(200L);
        progress.inspected(true);
        assertEquals(CookContainerSearchProgress.Phase.CARRYING, progress.phase());
    }

    @Test
    void pathFailureAdvancesWithoutExaminingTheUnreachableContainer() {
        CookContainerSearchProgress progress = new CookContainerSearchProgress(2, 60);

        progress.pathFailed();

        assertEquals(CookContainerSearchProgress.Phase.MOVING, progress.phase());
        assertEquals(1, progress.currentIndex().orElseThrow());
    }

    @Test
    void driftingAwayDuringExaminationRetriesTheSameContainer() {
        CookContainerSearchProgress progress = new CookContainerSearchProgress(1, 60);

        progress.arrived(100L);
        progress.interactionInterrupted();

        assertEquals(CookContainerSearchProgress.Phase.MOVING, progress.phase());
        assertEquals(0, progress.currentIndex().orElseThrow());
    }
}
