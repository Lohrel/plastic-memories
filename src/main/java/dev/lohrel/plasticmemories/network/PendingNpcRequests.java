package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side: futures waiting for a server reply, matched by request id and NPC id. A reply
 * for the wrong NPC, an unknown id, or a second reply to the same id is ignored.
 */
public final class PendingNpcRequests<T> {
    public static final PendingNpcRequests<CookAvailability> COOK_AVAILABILITY = new PendingNpcRequests<>();
    public static final PendingNpcRequests<NpcProfileResponse> PROFILES = new PendingNpcRequests<>();

    private final ConcurrentHashMap<Long, Pending<T>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<T> expect(UUID npcId, long requestId) {
        if (requestId <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("requestId must be positive"));
        }
        var future = new CompletableFuture<T>();
        var entry = new Pending<>(npcId, future);
        if (pending.putIfAbsent(requestId, entry) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate request id"));
        }
        // Also covers timeouts: however the future ends, stop waiting for it.
        future.whenComplete((ignored, error) -> pending.remove(requestId, entry));
        return future;
    }

    public void complete(UUID npcId, long requestId, T value) {
        Pending<T> entry = pending.get(requestId);
        if (entry != null && entry.npcId().equals(npcId) && pending.remove(requestId, entry)) {
            entry.future().complete(value);
        }
    }

    /** Fails every waiting future, e.g. on disconnect. */
    public void clear() {
        pending.values().forEach(entry -> entry.future().completeExceptionally(
                new CancellationException("Disconnected")));
        pending.clear();
    }

    private record Pending<T>(UUID npcId, CompletableFuture<T> future) {
    }
}
