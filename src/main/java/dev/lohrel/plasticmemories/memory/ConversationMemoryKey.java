package dev.lohrel.plasticmemories.memory;

import java.util.Objects;
import java.util.UUID;

public record ConversationMemoryKey(String worldIdentity, UUID playerId, UUID npcId) {
    public static final int MAX_WORLD_IDENTITY_LENGTH = 2_048;

    public ConversationMemoryKey {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        if (worldIdentity.isBlank() || worldIdentity.length() > MAX_WORLD_IDENTITY_LENGTH) {
            throw new IllegalArgumentException("Invalid world identity.");
        }
    }
}
