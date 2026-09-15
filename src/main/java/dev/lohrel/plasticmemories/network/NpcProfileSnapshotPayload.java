package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcProfileSnapshotPayload(
        UUID npcId,
        long requestId,
        NpcProfileResultCode result,
        NpcProfile profile,
        boolean canEdit) implements CustomPacketPayload {
    public static final Type<NpcProfileSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlasticMemories.MOD_ID, "npc_profile_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcProfileSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.npcId());
                buffer.writeLong(payload.requestId());
                buffer.writeEnum(payload.result());
                NpcProfileUpdatePayload.writeProfile(buffer, payload.profile());
                buffer.writeBoolean(payload.canEdit());
            },
            buffer -> new NpcProfileSnapshotPayload(
                    buffer.readUUID(),
                    buffer.readLong(),
                    buffer.readEnum(NpcProfileResultCode.class),
                    NpcProfileUpdatePayload.readProfile(buffer),
                    buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
