package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.network.SkillRequestPayload;
import dev.lohrel.plasticmemories.network.SkillResultCode;
import dev.lohrel.plasticmemories.network.SkillResultPayload;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import dev.lohrel.plasticmemories.skill.SkillRequestContext;
import dev.lohrel.plasticmemories.skill.SkillRequestDecision;
import dev.lohrel.plasticmemories.skill.SkillRequestGate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server side of a skill request: validates everything, then starts the task. */
public final class SkillServerPayloadHandler {
    private static final SkillRequestGate REQUEST_GATE = new SkillRequestGate(40, ServerNpcs.INTERACTION_RANGE);
    // Flood guard only: the gate above does replay and cooldown checks with its own result codes.
    private static final PlayerRequestLimiter FLOOD_GUARD = new PlayerRequestLimiter(1);

    private SkillServerPayloadHandler() {
    }

    public static void handle(SkillRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long gameTick = player.serverLevel().getGameTime();
        // At most one packet per player per tick. Extras are dropped without a reply.
        if (!FLOOD_GUARD.allowPacket(player.getUUID(), gameTick)) {
            return;
        }
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
        Optional<NpcHandle> npc = ServerNpcs.usable(entity);
        if (npc.isPresent() && !npc.orElseThrow().canAssignSkill(player)) {
            respond(player, payload, SkillResultCode.PERMISSION_DENIED);
            return;
        }
        SkillRequestContext requestContext = new SkillRequestContext(
                // A missing entity is not "unsupported"; it fails the validNpc check as INVALID_NPC instead.
                entity == null || ServerNpcs.isSupported(entity),
                npc.isPresent(),
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

    static void forgetPlayer(UUID playerId) {
        REQUEST_GATE.forgetPlayer(playerId);
        FLOOD_GUARD.forget(playerId);
    }

    static void reset() {
        REQUEST_GATE.clear();
        FLOOD_GUARD.clear();
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
