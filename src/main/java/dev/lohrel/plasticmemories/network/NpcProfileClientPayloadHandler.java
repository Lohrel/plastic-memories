package dev.lohrel.plasticmemories.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NpcProfileClientPayloadHandler {
    private NpcProfileClientPayloadHandler() {
    }

    public static void handle(NpcProfileSnapshotPayload payload, IPayloadContext context) {
        PendingNpcRequests.PROFILES.complete(
                payload.npcId(),
                payload.requestId(),
                new NpcProfileResponse(payload.result(), payload.profile(), payload.canEdit()));
    }
}
