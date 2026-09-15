package dev.lohrel.plasticmemories.conversation;

import java.util.List;

public final class NpcTargetResolver {
    private NpcTargetResolver() {
    }

    public static NpcTargetResolution resolve(String requestedName, List<NpcCandidate> candidates, double maxDistance) {
        double maxDistanceSquared = maxDistance * maxDistance;
        var matches = candidates.stream()
                .filter(candidate -> "mca".equals(candidate.namespace()))
                .filter(candidate -> candidate.distanceSquared() <= maxDistanceSquared)
                .filter(candidate -> candidate.name().equalsIgnoreCase(requestedName))
                .toList();
        if (matches.isEmpty()) {
            return NpcTargetResolution.notFound();
        }
        if (matches.size() > 1) {
            return NpcTargetResolution.ambiguous();
        }
        NpcCandidate match = matches.getFirst();
        return NpcTargetResolution.found(new ConversationTarget(match.npcId(), match.name()));
    }
}
