package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.adapter.mca.McaNpcResolver;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import dev.lohrel.plasticmemories.server.CookAvailabilityResolver;
import dev.lohrel.plasticmemories.server.CookContainerFoodResolver;
import dev.lohrel.plasticmemories.server.CookFoodCandidate;
import dev.lohrel.plasticmemories.server.CookFoodPlanner;
import dev.lohrel.plasticmemories.server.ServerSkillTasks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NpcCapabilityServerPayloadHandler {
    private static final double MAXIMUM_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final int REQUEST_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LATEST_REQUEST_IDS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PACKET_TICK = new HashMap<>();

    private NpcCapabilityServerPayloadHandler() {
    }

    public static void handle(NpcCapabilityRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long gameTick = player.serverLevel().getGameTime();
        UUID playerId = player.getUUID();
        long latestRequestId = LATEST_REQUEST_IDS.getOrDefault(playerId, 0L);
        if (payload.requestId() <= latestRequestId
                || NEXT_PACKET_TICK.getOrDefault(playerId, 0L) > gameTick) {
            respond(player, payload, CookAvailability.BUSY);
            return;
        }
        LATEST_REQUEST_IDS.put(playerId, payload.requestId());
        NEXT_PACKET_TICK.put(playerId, gameTick + REQUEST_COOLDOWN_TICKS);

        Entity entity = player.serverLevel().getEntity(payload.npcId());
        Optional<NpcHandle> npc = entity == null ? Optional.empty() : McaNpcResolver.resolve(entity);
        boolean validNpc = npc.map(handle -> handle.entity().isAlive()
                        && !handle.entity().isRemoved()
                        && (!(handle.entity() instanceof AgeableMob ageable) || !ageable.isBaby()))
                .orElse(false);
        boolean eligible = validNpc
                && !player.isSpectator()
                && player.distanceToSqr(entity) <= MAXIMUM_DISTANCE_SQUARED
                && npc.orElseThrow().canAssignSkill(player);
        boolean busy = validNpc && (ServerSkillTasks.isBusy(payload.npcId())
                || !npc.orElseThrow().availableForSkill());
        List<CookFoodCandidate> candidates = validNpc
                ? inventoryCandidates(npc.orElseThrow().inventory())
                : List.of();
        boolean nearbyContainerHasFood = eligible
                && !busy
                && CookFoodPlanner.selectSlot(candidates).isEmpty()
                && CookContainerFoodResolver.hasNearbyFood(
                        player.serverLevel(), npc.orElseThrow(), player);
        respond(player, payload, CookAvailabilityResolver.resolve(
                eligible, busy, candidates, nearbyContainerHasFood));
    }

    public static void forgetPlayer(UUID playerId) {
        LATEST_REQUEST_IDS.remove(playerId);
        NEXT_PACKET_TICK.remove(playerId);
    }

    public static void reset() {
        LATEST_REQUEST_IDS.clear();
        NEXT_PACKET_TICK.clear();
    }

    private static List<CookFoodCandidate> inventoryCandidates(Container inventory) {
        var candidates = new ArrayList<CookFoodCandidate>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            candidates.add(new CookFoodCandidate(slot, stack.getCount(), stack.has(DataComponents.FOOD)));
        }
        return candidates;
    }

    private static void respond(
            ServerPlayer player, NpcCapabilityRequestPayload request, CookAvailability availability) {
        PacketDistributor.sendToPlayer(
                player, new NpcCapabilityPayload(request.npcId(), request.requestId(), availability));
    }
}
