package dev.lohrel.plasticmemories.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PlasticMemoriesNetwork {
    // Bump whenever a payload or enum changes. Clients and servers with different versions refuse to connect.
    private static final String PROTOCOL_VERSION = "4";

    private PlasticMemoriesNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                SkillRequestPayload.TYPE,
                SkillRequestPayload.STREAM_CODEC,
                SkillServerPayloadHandler::handle);
        registrar.playToClient(
                SkillResultPayload.TYPE,
                SkillResultPayload.STREAM_CODEC,
                SkillClientPayloadHandler::handle);
        registrar.playToServer(
                NpcCapabilityRequestPayload.TYPE,
                NpcCapabilityRequestPayload.STREAM_CODEC,
                NpcCapabilityServerPayloadHandler::handle);
        registrar.playToClient(
                NpcCapabilityPayload.TYPE,
                NpcCapabilityPayload.STREAM_CODEC,
                NpcCapabilityClientPayloadHandler::handle);
        registrar.playToServer(
                NpcProfileRequestPayload.TYPE,
                NpcProfileRequestPayload.STREAM_CODEC,
                NpcProfileServerPayloadHandler::handleRequest);
        registrar.playToServer(
                NpcProfileUpdatePayload.TYPE,
                NpcProfileUpdatePayload.STREAM_CODEC,
                NpcProfileServerPayloadHandler::handleUpdate);
        registrar.playToClient(
                NpcProfileSnapshotPayload.TYPE,
                NpcProfileSnapshotPayload.STREAM_CODEC,
                NpcProfileClientPayloadHandler::handle);
    }
}
