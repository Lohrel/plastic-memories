package dev.lohrel.plasticmemories;

import dev.lohrel.plasticmemories.network.NpcCapabilityClientPayloadHandler;
import dev.lohrel.plasticmemories.network.NpcCapabilityPayload;
import dev.lohrel.plasticmemories.network.NpcCapabilityRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileClientPayloadHandler;
import dev.lohrel.plasticmemories.network.NpcProfileRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileSnapshotPayload;
import dev.lohrel.plasticmemories.network.NpcProfileUpdatePayload;
import dev.lohrel.plasticmemories.network.SkillClientPayloadHandler;
import dev.lohrel.plasticmemories.network.SkillRequestPayload;
import dev.lohrel.plasticmemories.network.SkillResultPayload;
import dev.lohrel.plasticmemories.server.NpcCapabilityServerPayloadHandler;
import dev.lohrel.plasticmemories.server.NpcProfileServerPayloadHandler;
import dev.lohrel.plasticmemories.server.SkillServerPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers every packet with its handler. Lives at the root because it wires the network, client and server packages together. */
public final class PlasticMemoriesNetwork {
    // Bump whenever a payload or enum changes. Clients and servers with different versions refuse to connect.
    private static final String PROTOCOL_VERSION = "5";

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
