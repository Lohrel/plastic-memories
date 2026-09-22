package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.PlasticMemories;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "make this NPC do this skill". Deliberately carries no chat text. */
public record SkillRequestPayload(UUID npcId, long requestId, String skillId) implements CustomPacketPayload {
    public static final Type<SkillRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlasticMemories.MOD_ID, "skill_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.npcId());
                buffer.writeLong(payload.requestId());
                buffer.writeUtf(payload.skillId(), 16);
            },
            buffer -> new SkillRequestPayload(buffer.readUUID(), buffer.readLong(), buffer.readUtf(16)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
