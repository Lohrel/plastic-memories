package dev.lohrel.plasticmemories.server;

import java.util.Comparator;
import java.util.List;

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
