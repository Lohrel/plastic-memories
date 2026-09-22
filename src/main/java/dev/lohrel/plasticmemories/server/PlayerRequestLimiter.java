package dev.lohrel.plasticmemories.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player guard for incoming requests: ids must strictly increase (no replays) and requests
 * must be at least {@code cooldownTicks} apart. Only accepted requests update the state.
 */
final class PlayerRequestLimiter {
    private final long cooldownTicks;
    private final Map<UUID, Long> latestRequestIds = new HashMap<>();
    private final Map<UUID, Long> nextAllowedTick = new HashMap<>();

    PlayerRequestLimiter(long cooldownTicks) {
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must not be negative");
        }
        this.cooldownTicks = cooldownTicks;
    }

    Admission admit(UUID playerId, long requestId, long gameTick) {
        if (requestId <= latestRequestIds.getOrDefault(playerId, 0L)) {
            return Admission.REPLAYED;
        }
        if (nextAllowedTick.getOrDefault(playerId, 0L) > gameTick) {
            return Admission.RATE_LIMITED;
        }
        latestRequestIds.put(playerId, requestId);
        nextAllowedTick.put(playerId, gameTick + cooldownTicks);
        return Admission.ACCEPTED;
    }

    /** Spacing check only, for callers that track request ids elsewhere. */
    boolean allowPacket(UUID playerId, long gameTick) {
        if (nextAllowedTick.getOrDefault(playerId, 0L) > gameTick) {
            return false;
        }
        nextAllowedTick.put(playerId, gameTick + cooldownTicks);
        return true;
    }

    void forget(UUID playerId) {
        latestRequestIds.remove(playerId);
        nextAllowedTick.remove(playerId);
    }

    void clear() {
        latestRequestIds.clear();
        nextAllowedTick.clear();
    }

    enum Admission {
        ACCEPTED,
        REPLAYED,
        RATE_LIMITED
    }
}
