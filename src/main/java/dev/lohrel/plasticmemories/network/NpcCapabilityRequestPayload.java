package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.PlasticMemories;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcCapabilityRequestPayload(UUID npcId, long requestId) implements CustomPacketPayload {
    public static final Type<NpcCapabilityRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlasticMemories.MOD_ID, "npc_capability_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcCapabilityRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.npcId());
                buffer.writeLong(payload.requestId());
            },
            buffer -> new NpcCapabilityRequestPayload(buffer.readUUID(), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
