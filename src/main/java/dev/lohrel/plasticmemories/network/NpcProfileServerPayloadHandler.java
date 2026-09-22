package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.adapter.mca.McaNpcResolver;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import dev.lohrel.plasticmemories.server.NpcProfileAuthorization;
import dev.lohrel.plasticmemories.server.NpcProfilesSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Serves and updates the shared character profile of a nearby NPC. Anyone nearby can read; only operators can write. */
public final class NpcProfileServerPayloadHandler {
    private static final double MAXIMUM_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final Map<UUID, Long> LATEST_REQUEST_IDS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PACKET_TICK = new HashMap<>();

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
        if (!canEdit(player)) {
            NpcProfile current = NpcProfilesSavedData.get(player.serverLevel()).getProfile(payload.npcId());
            respond(player, payload.npcId(), payload.requestId(), NpcProfileResultCode.PERMISSION_DENIED, current, false);
            return;
        }

        NpcProfilesSavedData.get(player.serverLevel()).putProfile(payload.npcId(), payload.profile());
        respond(player, payload.npcId(), payload.requestId(), NpcProfileResultCode.SUCCESS, payload.profile(), true);
    }

    public static void forgetPlayer(UUID playerId) {
        LATEST_REQUEST_IDS.remove(playerId);
        NEXT_PACKET_TICK.remove(playerId);
    }

    public static void reset() {
        LATEST_REQUEST_IDS.clear();
        NEXT_PACKET_TICK.clear();
    }

    /** Replay and rate-limit check shared by reads and updates. */
    private static NpcProfileResultCode admit(ServerPlayer player, long requestId) {
        if (requestId <= 0) {
            return NpcProfileResultCode.INVALID_REQUEST;
        }
        UUID playerId = player.getUUID();
        long gameTick = player.serverLevel().getGameTime();
        long latestRequestId = LATEST_REQUEST_IDS.getOrDefault(playerId, 0L);
        if (requestId <= latestRequestId) {
            return NpcProfileResultCode.REPLAYED;
        }
        if (NEXT_PACKET_TICK.getOrDefault(playerId, 0L) > gameTick) {
            return NpcProfileResultCode.RATE_LIMITED;
        }
        LATEST_REQUEST_IDS.put(playerId, requestId);
        NEXT_PACKET_TICK.put(playerId, gameTick + 1);
        return NpcProfileResultCode.SUCCESS;
    }

    private static Optional<NpcHandle> resolveNearbyNpc(ServerPlayer player, UUID npcId) {
        Entity entity = player.serverLevel().getEntity(npcId);
        if (entity == null || player.distanceToSqr(entity) > MAXIMUM_DISTANCE_SQUARED) {
            return Optional.empty();
        }
        return McaNpcResolver.resolve(entity).filter(handle -> handle.entity().isAlive()
                && !handle.entity().isRemoved()
                && (!(handle.entity() instanceof AgeableMob ageable) || !ageable.isBaby()));
    }

    private static NpcProfileResultCode invalidNpcResult(ServerPlayer player, UUID npcId) {
        Entity entity = player.serverLevel().getEntity(npcId);
        return entity != null && player.distanceToSqr(entity) > MAXIMUM_DISTANCE_SQUARED
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
