package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.PlasticMemories;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkillResultPayload(UUID npcId, long requestId, SkillResultCode result) implements CustomPacketPayload {
    public static final Type<SkillResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlasticMemories.MOD_ID, "skill_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.npcId());
                buffer.writeLong(payload.requestId());
                buffer.writeEnum(payload.result());
            },
            buffer -> new SkillResultPayload(
                    buffer.readUUID(), buffer.readLong(), buffer.readEnum(SkillResultCode.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
