package dev.lohrel.plasticmemories.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NpcCapabilityClientPayloadHandler {
    private NpcCapabilityClientPayloadHandler() {
    }

    public static void handle(NpcCapabilityPayload payload, IPayloadContext context) {
        PendingNpcRequests.COOK_AVAILABILITY.complete(
                payload.npcId(), payload.requestId(), payload.cookAvailability());
    }
}
