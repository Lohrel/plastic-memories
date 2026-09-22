package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.network.NpcProfileRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileResultCode;
import dev.lohrel.plasticmemories.network.NpcProfileSnapshotPayload;
import dev.lohrel.plasticmemories.network.NpcProfileUpdatePayload;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Serves and updates the shared character profile of a nearby NPC. Anyone nearby can read; only operators can write. */
public final class NpcProfileServerPayloadHandler {
    // Reads and updates share one limiter, so ids must increase across both.
    private static final PlayerRequestLimiter LIMITER = new PlayerRequestLimiter(1);

    private NpcProfileServerPayloadHandler() {
    }

    public static void handleRequest(NpcProfileRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        NpcProfileResultCode admission = admit(player, payload.requestId());
        if (admission != NpcProfileResultCode.SUCCESS) {
            respond(player, payload.npcId(), payload.requestId(), admission, NpcProfile.empty(), false);
            return;
        }
        Optional<NpcHandle> npc = resolveNearbyNpc(player, payload.npcId());
        if (npc.isEmpty()) {
            respond(player, payload.npcId(), payload.requestId(), invalidNpcResult(player, payload.npcId()), NpcProfile.empty(), false);
            return;
        }
        NpcProfile profile = NpcProfilesSavedData.get(player.serverLevel()).getProfile(payload.npcId());
        respond(player, payload.npcId(), payload.requestId(), NpcProfileResultCode.SUCCESS, profile, canEdit(player));
    }

    public static void handleUpdate(NpcProfileUpdatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        NpcProfileResultCode admission = admit(player, payload.requestId());
        if (admission != NpcProfileResultCode.SUCCESS) {
            respond(player, payload.npcId(), payload.requestId(), admission, NpcProfile.empty(), false);
            return;
        }
        Optional<NpcHandle> npc = resolveNearbyNpc(player, payload.npcId());
        if (npc.isEmpty()) {
            respond(player, payload.npcId(), payload.requestId(), invalidNpcResult(player, payload.npcId()), NpcProfile.empty(), false);
            return;
        }
        NpcProfilesSavedData data = NpcProfilesSavedData.get(player.serverLevel());
        if (!canEdit(player)) {
            respond(player, payload.npcId(), payload.requestId(), NpcProfileResultCode.PERMISSION_DENIED,
                    data.getProfile(payload.npcId()), false);
            return;
        }
        if (!data.putProfile(payload.npcId(), payload.profile())) {
            respond(player, payload.npcId(), payload.requestId(), NpcProfileResultCode.STORAGE_READ_ONLY,
                    data.getProfile(payload.npcId()), true);
            return;
        }
        respond(player, payload.npcId(), payload.requestId(), NpcProfileResultCode.SUCCESS, payload.profile(), true);
    }

    static void forgetPlayer(UUID playerId) {
        LIMITER.forget(playerId);
    }

    static void reset() {
        LIMITER.clear();
    }

    private static NpcProfileResultCode admit(ServerPlayer player, long requestId) {
        if (requestId <= 0) {
            return NpcProfileResultCode.INVALID_REQUEST;
        }
        return switch (LIMITER.admit(player.getUUID(), requestId, player.serverLevel().getGameTime())) {
            case ACCEPTED -> NpcProfileResultCode.SUCCESS;
            case REPLAYED -> NpcProfileResultCode.REPLAYED;
            case RATE_LIMITED -> NpcProfileResultCode.RATE_LIMITED;
        };
    }

    private static Optional<NpcHandle> resolveNearbyNpc(ServerPlayer player, UUID npcId) {
        Entity entity = player.serverLevel().getEntity(npcId);
        if (entity == null || player.distanceToSqr(entity) > ServerNpcs.INTERACTION_RANGE_SQUARED) {
            return Optional.empty();
        }
        return ServerNpcs.usable(entity);
    }

    private static NpcProfileResultCode invalidNpcResult(ServerPlayer player, UUID npcId) {
        Entity entity = player.serverLevel().getEntity(npcId);
        return entity != null && player.distanceToSqr(entity) > ServerNpcs.INTERACTION_RANGE_SQUARED
                ? NpcProfileResultCode.OUT_OF_RANGE
                : NpcProfileResultCode.INVALID_NPC;
    }

    private static boolean canEdit(ServerPlayer player) {
        var server = player.getServer();
        return NpcProfileAuthorization.canEdit(
                server.getPlayerList().isOp(player.getGameProfile()),
                server.isSingleplayerOwner(player.getGameProfile()));
    }

    private static void respond(
            ServerPlayer player,
            UUID npcId,
            long requestId,
            NpcProfileResultCode result,
            NpcProfile profile,
            boolean canEdit) {
        PacketDistributor.sendToPlayer(
                player, new NpcProfileSnapshotPayload(npcId, requestId, result, profile, canEdit));
    }
}
