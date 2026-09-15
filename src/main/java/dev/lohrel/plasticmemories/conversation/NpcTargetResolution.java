package dev.lohrel.plasticmemories.conversation;

import java.util.Objects;
import java.util.Optional;

public record NpcTargetResolution(TargetResolutionStatus status, Optional<ConversationTarget> target) {
    public NpcTargetResolution {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(target, "target");
    }

    public static NpcTargetResolution found(ConversationTarget target) {
        return new NpcTargetResolution(TargetResolutionStatus.FOUND, Optional.of(target));
    }

    public static NpcTargetResolution notFound() {
        return new NpcTargetResolution(TargetResolutionStatus.NOT_FOUND, Optional.empty());
    }

    public static NpcTargetResolution ambiguous() {
        return new NpcTargetResolution(TargetResolutionStatus.AMBIGUOUS, Optional.empty());
    }
}
