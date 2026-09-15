package dev.lohrel.plasticmemories.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ConversationStateTest {
    @Test
    void exposesTheSelectedPrivateConversationTarget() {
        ConversationState state = new ConversationState();
        ConversationTarget john = new ConversationTarget(UUID.randomUUID(), "John");
        state.enter(john);

        assertTrue(state.target().isPresent());
        assertEquals(john, state.target().orElseThrow());
    }

    @Test
    void routesOrdinaryTextToServerWhileNoPrivateConversationIsActive() {
        ConversationState state = new ConversationState();

        OutgoingChatRoute route = state.route("Hello everyone");

        assertEquals(OutgoingChatRoute.SERVER_CHAT, route);
    }

    @Test
    void routesTextToServerAfterLeavingPrivateConversation() {
        ConversationState state = new ConversationState();
        state.enter(new ConversationTarget(UUID.randomUUID(), "John"));
        state.leave();

        OutgoingChatRoute route = state.route("Hello everyone");

        assertEquals(OutgoingChatRoute.SERVER_CHAT, route);
    }

    @Test
    void routesOrdinaryTextLocallyWhilePrivateConversationIsActive() {
        ConversationState state = new ConversationState();
        state.enter(new ConversationTarget(UUID.randomUUID(), "John"));

        OutgoingChatRoute route = state.route("I am hungry");

        assertEquals(OutgoingChatRoute.LOCAL_PRIVATE, route);
    }
}
