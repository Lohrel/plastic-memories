package dev.lohrel.plasticmemories.conversation;

import java.util.Objects;
import java.util.UUID;

public record NpcCandidate(UUID npcId, String name, String namespace, double distanceSquared) {
    public NpcCandidate {
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
    }
}
