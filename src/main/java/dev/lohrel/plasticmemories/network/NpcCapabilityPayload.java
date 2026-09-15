package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcCapabilityPayload(
        UUID npcId, long requestId, CookAvailability cookAvailability) implements CustomPacketPayload {
    public static final Type<NpcCapabilityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlasticMemories.MOD_ID, "npc_capability"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcCapabilityPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.npcId());
                buffer.writeLong(payload.requestId());
                buffer.writeEnum(payload.cookAvailability());
            },
            buffer -> new NpcCapabilityPayload(
                    buffer.readUUID(), buffer.readLong(), buffer.readEnum(CookAvailability.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
