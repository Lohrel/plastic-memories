package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.adapter.mca.McaNpcResolver;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import dev.lohrel.plasticmemories.server.ServerSkillTasks;
import dev.lohrel.plasticmemories.skill.SkillRequestContext;
import dev.lohrel.plasticmemories.skill.SkillRequestDecision;
import dev.lohrel.plasticmemories.skill.SkillRequestGate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class SkillServerPayloadHandler {
    private static final SkillRequestGate REQUEST_GATE = new SkillRequestGate(40, 32.0);
    private static final Map<UUID, Long> NEXT_PACKET_TICK = new HashMap<>();

    private SkillServerPayloadHandler() {
    }

    public static void handle(SkillRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long gameTick = player.serverLevel().getGameTime();
        if (NEXT_PACKET_TICK.getOrDefault(player.getUUID(), 0L) > gameTick) {
            return;
        }
        NEXT_PACKET_TICK.put(player.getUUID(), gameTick + 1);
        if (payload.requestId() <= 0) {
            respond(player, payload, SkillResultCode.INVALID_REQUEST);
            return;
        }
        if (!"COOK".equals(payload.skillId())) {
            respond(player, payload, SkillResultCode.UNSUPPORTED_SKILL);
            return;
        }
        if (player.isSpectator()) {
            respond(player, payload, SkillResultCode.PERMISSION_DENIED);
            return;
        }

        Entity entity = player.serverLevel().getEntity(payload.npcId());
        Optional<NpcHandle> npc = entity == null ? Optional.empty() : McaNpcResolver.resolve(entity);
        boolean validNpc = npc.map(handle -> handle.entity().isAlive()
                        && !handle.entity().isRemoved()
                        && (!(handle.entity() instanceof AgeableMob ageable) || !ageable.isBaby()))
                .orElse(false);
        if (validNpc && !npc.orElseThrow().canAssignSkill(player)) {
            respond(player, payload, SkillResultCode.PERMISSION_DENIED);
            return;
        }
        SkillRequestContext requestContext = new SkillRequestContext(
                entity == null || npc.isPresent(),
                validNpc,
                ServerSkillTasks.isBusy(payload.npcId())
                        || npc.map(handle -> !handle.availableForSkill()).orElse(false),
                entity == null ? Double.POSITIVE_INFINITY : player.distanceToSqr(entity));
        SkillRequestDecision decision = REQUEST_GATE.evaluate(
                player.getUUID(),
                payload.npcId(),
                payload.requestId(),
                gameTick,
                requestContext);
        if (decision != SkillRequestDecision.ACCEPTED) {
            respond(player, payload, resultFor(decision));
            return;
        }

        ServerSkillTasks.startCook(player, npc.orElseThrow(), payload.requestId());
    }

    public static void forgetPlayer(UUID playerId) {
        REQUEST_GATE.forgetPlayer(playerId);
        NEXT_PACKET_TICK.remove(playerId);
    }

    public static void reset() {
        REQUEST_GATE.clear();
        NEXT_PACKET_TICK.clear();
    }

    private static SkillResultCode resultFor(SkillRequestDecision decision) {
        return switch (decision) {
            case ACCEPTED -> throw new IllegalArgumentException("Accepted has no failure result");
            case INVALID_NPC -> SkillResultCode.INVALID_NPC;
            case UNSUPPORTED_NPC -> SkillResultCode.UNSUPPORTED_NPC;
            case OUT_OF_RANGE -> SkillResultCode.OUT_OF_RANGE;
            case NPC_BUSY -> SkillResultCode.NPC_BUSY;
            case RATE_LIMITED -> SkillResultCode.RATE_LIMITED;
            case REPLAYED -> SkillResultCode.REPLAYED;
        };
    }

    private static void respond(ServerPlayer player, SkillRequestPayload request, SkillResultCode result) {
        PacketDistributor.sendToPlayer(
                player, new SkillResultPayload(request.npcId(), request.requestId(), result));
    }
}
