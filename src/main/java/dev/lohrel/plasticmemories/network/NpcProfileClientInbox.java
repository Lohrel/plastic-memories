package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Matches incoming profile replies to the futures the client is waiting on, by request id. */
public final class NpcProfileClientInbox {
    private static final NpcProfileClientInbox SHARED = new NpcProfileClientInbox();

    private final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();

    public static NpcProfileClientInbox shared() {
        return SHARED;
    }

    public CompletableFuture<NpcProfileResponse> expect(UUID npcId, long requestId) {
        if (requestId <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("requestId must be positive"));
        }
        var future = new CompletableFuture<NpcProfileResponse>();
        var entry = new Pending(npcId, future);
        if (pending.putIfAbsent(requestId, entry) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate profile request ID"));
        }
        future.whenComplete((ignored, error) -> pending.remove(requestId, entry));
        return future;
    }

    public void complete(
            UUID npcId,
            long requestId,
            NpcProfileResultCode result,
            NpcProfile profile,
            boolean canEdit) {
        Pending entry = pending.get(requestId);
        if (entry == null || !entry.npcId().equals(npcId) || !pending.remove(requestId, entry)) {
            return;
        }
        entry.future().complete(new NpcProfileResponse(result, profile, canEdit));
    }

    public void clear() {
        pending.values().forEach(entry -> entry.future().completeExceptionally(
                new CancellationException("Disconnected before NPC profile response")));
        pending.clear();
    }

    private record Pending(UUID npcId, CompletableFuture<NpcProfileResponse> future) {
    }
}
