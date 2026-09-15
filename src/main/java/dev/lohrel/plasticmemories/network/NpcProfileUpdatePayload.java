package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcProfileUpdatePayload(UUID npcId, long requestId, NpcProfile profile)
        implements CustomPacketPayload {
    public static final Type<NpcProfileUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlasticMemories.MOD_ID, "npc_profile_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcProfileUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.npcId());
                buffer.writeLong(payload.requestId());
                writeProfile(buffer, payload.profile());
            },
            buffer -> new NpcProfileUpdatePayload(
                    buffer.readUUID(),
                    buffer.readLong(),
                    readProfile(buffer)));

    static void writeProfile(RegistryFriendlyByteBuf buffer, NpcProfile profile) {
        buffer.writeUtf(profile.description(), NpcProfile.MAX_DESCRIPTION_LENGTH);
        buffer.writeUtf(profile.personality(), NpcProfile.MAX_PERSONALITY_LENGTH);
        buffer.writeUtf(profile.appearance(), NpcProfile.MAX_APPEARANCE_LENGTH);
        buffer.writeUtf(profile.backstory(), NpcProfile.MAX_BACKSTORY_LENGTH);
    }

    static NpcProfile readProfile(RegistryFriendlyByteBuf buffer) {
        return NpcProfile.create(
                buffer.readUtf(NpcProfile.MAX_DESCRIPTION_LENGTH),
                buffer.readUtf(NpcProfile.MAX_PERSONALITY_LENGTH),
                buffer.readUtf(NpcProfile.MAX_APPEARANCE_LENGTH),
                buffer.readUtf(NpcProfile.MAX_BACKSTORY_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
