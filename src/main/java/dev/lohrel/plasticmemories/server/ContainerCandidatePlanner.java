package dev.lohrel.plasticmemories.server;

import java.util.Comparator;
import java.util.List;

/** Picks the N closest containers. Ties are broken by position so the order is always the same. */
final class ContainerCandidatePlanner {
    private ContainerCandidatePlanner() {
    }

    static List<ContainerCandidate> nearest(
            List<ContainerCandidate> candidates, int maximumVisitedContainers) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(ContainerCandidate::distanceSquared)
                        .thenComparingInt(ContainerCandidate::x)
                        .thenComparingInt(ContainerCandidate::y)
                        .thenComparingInt(ContainerCandidate::z))
                .limit(Math.max(0, maximumVisitedContainers))
                .toList();
    }
}
