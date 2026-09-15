package dev.lohrel.plasticmemories.conversation;

import java.util.Objects;
import java.util.UUID;

public record ConversationTarget(UUID npcId, String name) {
    public ConversationTarget {
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(name, "name");
    }
}
