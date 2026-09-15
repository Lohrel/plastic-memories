package dev.lohrel.plasticmemories.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NpcCapabilityClientPayloadHandler {
    private NpcCapabilityClientPayloadHandler() {
    }

    public static void handle(NpcCapabilityPayload payload, IPayloadContext context) {
        NpcCapabilityClientInbox.shared().complete(
                payload.npcId(), payload.requestId(), payload.cookAvailability());
    }
}
