package dev.lohrel.plasticmemories.network;

import dev.lohrel.plasticmemories.npc.CookAvailability;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Matches incoming capability replies to the futures the client is waiting on, by request id. */
public final class NpcCapabilityClientInbox {
    private static final NpcCapabilityClientInbox SHARED = new NpcCapabilityClientInbox();

    private final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();

    public static NpcCapabilityClientInbox shared() {
        return SHARED;
    }

    public CompletableFuture<CookAvailability> expect(UUID npcId, long requestId) {
        if (requestId <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid request ID"));
        }
        var future = new CompletableFuture<CookAvailability>();
        var entry = new Pending(npcId, future);
        if (pending.putIfAbsent(requestId, entry) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate request ID"));
        }
        future.whenComplete((result, error) -> pending.remove(requestId, entry));
        return future;
    }

    public void complete(UUID npcId, long requestId, CookAvailability availability) {
        Pending entry = pending.get(requestId);
        if (entry != null && entry.npcId().equals(npcId) && pending.remove(requestId, entry)) {
            entry.future().complete(availability);
        }
    }

    public void clear() {
        pending.values().forEach(entry -> entry.future().completeExceptionally(
                new CancellationException("Disconnected")));
        pending.clear();
    }

    private record Pending(UUID npcId, CompletableFuture<CookAvailability> future) {
    }
}
