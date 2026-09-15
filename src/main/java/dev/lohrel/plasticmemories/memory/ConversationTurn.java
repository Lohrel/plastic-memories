package dev.lohrel.plasticmemories.memory;

import java.util.Objects;

public record ConversationTurn(String playerMessage, String npcReply) {
    public static final int MAX_PLAYER_MESSAGE_LENGTH = 512;
    public static final int MAX_NPC_REPLY_LENGTH = 4_096;

    public ConversationTurn {
        Objects.requireNonNull(playerMessage, "playerMessage");
        Objects.requireNonNull(npcReply, "npcReply");
        if (playerMessage.isBlank() || playerMessage.length() > MAX_PLAYER_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Invalid player message.");
        }
        if (npcReply.isBlank() || npcReply.length() > MAX_NPC_REPLY_LENGTH) {
            throw new IllegalArgumentException("Invalid NPC reply.");
        }
    }
}
