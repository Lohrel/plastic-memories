package dev.lohrel.plasticmemories.conversation;

import java.util.Objects;
import java.util.Optional;

/** Which NPC (if any) the player is privately talking to. While one is set, chat is kept off the server. */
public final class ConversationState {
    private ConversationTarget target;

    public void enter(ConversationTarget target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    public void leave() {
        target = null;
    }

    public Optional<ConversationTarget> target() {
        return Optional.ofNullable(target);
    }

    public OutgoingChatRoute route(String message) {
        Objects.requireNonNull(message, "message");
        return target == null ? OutgoingChatRoute.SERVER_CHAT : OutgoingChatRoute.LOCAL_PRIVATE;
    }
}
