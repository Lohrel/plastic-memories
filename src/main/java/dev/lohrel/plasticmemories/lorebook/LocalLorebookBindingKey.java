package dev.lohrel.plasticmemories.lorebook;

import java.util.Objects;
import java.util.UUID;

/** Identifies one conversation (world + player + NPC) that a card can be bound to. */
public record LocalLorebookBindingKey(String worldIdentity, UUID playerId, UUID npcId) {
    public LocalLorebookBindingKey {
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        playerId = Objects.requireNonNull(playerId, "playerId");
        npcId = Objects.requireNonNull(npcId, "npcId");
        if (worldIdentity.isBlank() || worldIdentity.length() > 512) {
            throw new IllegalArgumentException("Invalid client-local world identity.");
        }
    }
}
