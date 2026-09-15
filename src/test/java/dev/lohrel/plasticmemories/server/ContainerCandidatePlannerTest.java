package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ContainerCandidatePlannerTest {
    @Test
    void ordersCandidatesNearestFirst() {
        var far = new ContainerCandidate(8, 2, 1, 65.0);
        var near = new ContainerCandidate(2, 2, 1, 5.0);
        var middle = new ContainerCandidate(4, 2, 1, 17.0);

        assertEquals(List.of(near, middle, far),
                ContainerCandidatePlanner.nearest(List.of(far, near, middle), 10));
    }

    @Test
    void examinesNoMoreThanTheConfiguredMaximum() {
        var first = new ContainerCandidate(1, 0, 0, 1.0);
        var second = new ContainerCandidate(2, 0, 0, 4.0);
        var third = new ContainerCandidate(3, 0, 0, 9.0);

        assertEquals(List.of(first, second),
                ContainerCandidatePlanner.nearest(List.of(third, first, second), 2));
    }

    @Test
    void breaksDistanceTiesByCoordinatesForDeterministicSearch() {
        var later = new ContainerCandidate(2, 0, 0, 4.0);
        var earlier = new ContainerCandidate(-2, 0, 0, 4.0);

        assertEquals(List.of(earlier, later),
                ContainerCandidatePlanner.nearest(List.of(later, earlier), 10));
    }
}
