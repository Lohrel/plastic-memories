package dev.lohrel.plasticmemories.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SkillRequestGate {
    private final long cooldownTicks;
    private final double maximumDistanceSquared;
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Map<UUID, Long> npcCooldowns = new HashMap<>();
    private final Map<UUID, Long> latestRequestIds = new HashMap<>();

    public SkillRequestGate(long cooldownTicks, double maximumDistance) {
        if (cooldownTicks < 0 || maximumDistance <= 0) {
            throw new IllegalArgumentException("Invalid gate bounds");
        }
        this.cooldownTicks = cooldownTicks;
        this.maximumDistanceSquared = maximumDistance * maximumDistance;
    }

    public SkillRequestDecision evaluate(
            UUID playerId,
            UUID npcId,
            long requestId,
            long gameTick,
            SkillRequestContext context) {
        if (!rememberRequest(playerId, requestId)) {
            return SkillRequestDecision.REPLAYED;
        }
        if (!context.supportedNpc()) {
            return SkillRequestDecision.UNSUPPORTED_NPC;
        }
        if (!context.validNpc()) {
            return SkillRequestDecision.INVALID_NPC;
        }
        if (!Double.isFinite(context.distanceSquared()) || context.distanceSquared() > maximumDistanceSquared) {
            return SkillRequestDecision.OUT_OF_RANGE;
        }
        if (context.busy() || npcCooldowns.getOrDefault(npcId, 0L) > gameTick) {
            return SkillRequestDecision.NPC_BUSY;
        }
        if (playerCooldowns.getOrDefault(playerId, 0L) > gameTick) {
            return SkillRequestDecision.RATE_LIMITED;
        }

        long cooldownEnd = gameTick + cooldownTicks;
        playerCooldowns.put(playerId, cooldownEnd);
        npcCooldowns.put(npcId, cooldownEnd);
        return SkillRequestDecision.ACCEPTED;
    }

    public void clear() {
        playerCooldowns.clear();
        npcCooldowns.clear();
        latestRequestIds.clear();
    }

    public void forgetPlayer(UUID playerId) {
        playerCooldowns.remove(playerId);
        latestRequestIds.remove(playerId);
    }

    private boolean rememberRequest(UUID playerId, long requestId) {
        long latest = latestRequestIds.getOrDefault(playerId, 0L);
        if (requestId <= latest) {
            return false;
        }
        latestRequestIds.put(playerId, requestId);
        return true;
    }
}
