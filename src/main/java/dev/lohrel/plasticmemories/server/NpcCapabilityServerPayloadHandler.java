package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.network.NpcCapabilityPayload;
import dev.lohrel.plasticmemories.network.NpcCapabilityRequestPayload;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Answers the client's "can this NPC COOK right now?" question before each chat message. */
public final class NpcCapabilityServerPayloadHandler {
    private static final PlayerRequestLimiter LIMITER = new PlayerRequestLimiter(20);

    private NpcCapabilityServerPayloadHandler() {
    }

    public static void handle(NpcCapabilityRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long gameTick = player.serverLevel().getGameTime();
        // Replayed or too frequent: answer BUSY rather than an error, the prompt only needs a hint.
        if (LIMITER.admit(player.getUUID(), payload.requestId(), gameTick) != PlayerRequestLimiter.Admission.ACCEPTED) {
            respond(player, payload, CookAvailability.BUSY);
            return;
        }

        Entity entity = player.serverLevel().getEntity(payload.npcId());
        Optional<NpcHandle> npc = ServerNpcs.usable(entity);
        boolean eligible = npc.isPresent()
                && !player.isSpectator()
                && player.distanceToSqr(entity) <= ServerNpcs.INTERACTION_RANGE_SQUARED
                && npc.orElseThrow().canAssignSkill(player);
        boolean busy = npc.isPresent() && (ServerSkillTasks.isBusy(payload.npcId())
                || !npc.orElseThrow().availableForSkill());
        List<CookFoodCandidate> candidates = npc
                .<List<CookFoodCandidate>>map(handle -> CookInventoryTransfer.foodCandidates(handle.inventory()))
                .orElse(List.of());
        // Scanning containers is the expensive part, so only do it when the NPC carries no food.
        boolean nearbyContainerHasFood = eligible
                && !busy
                && CookFoodPlanner.selectSlot(candidates).isEmpty()
                && CookContainerFoodResolver.hasNearbyFood(player.serverLevel(), npc.orElseThrow(), player);
        respond(player, payload, CookAvailabilityResolver.resolve(eligible, busy, candidates, nearbyContainerHasFood));
    }

    static void forgetPlayer(UUID playerId) {
        LIMITER.forget(playerId);
    }

    static void reset() {
        LIMITER.clear();
    }

    private static void respond(
            ServerPlayer player, NpcCapabilityRequestPayload request, CookAvailability availability) {
        PacketDistributor.sendToPlayer(
                player, new NpcCapabilityPayload(request.npcId(), request.requestId(), availability));
    }
}
