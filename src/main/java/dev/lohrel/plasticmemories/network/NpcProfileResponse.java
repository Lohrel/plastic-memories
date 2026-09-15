package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.npc.NpcProfile;

public record NpcProfileResponse(
        NpcProfileResultCode result, NpcProfile profile, boolean canEdit) {
}
