package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** In-memory map of NPC id to shared character profile. Missing NPCs get an empty profile. */
final class NpcProfileRegistry {
    private final Map<UUID, NpcProfile> profiles = new HashMap<>();

    NpcProfile get(UUID npcId) {
        return profiles.getOrDefault(Objects.requireNonNull(npcId), NpcProfile.empty());
    }

    void put(UUID npcId, NpcProfile profile) {
        profiles.put(Objects.requireNonNull(npcId), Objects.requireNonNull(profile));
    }

    Map<UUID, NpcProfile> entries() {
        return Map.copyOf(profiles);
    }
}
