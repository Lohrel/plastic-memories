package dev.lohrel.plasticmemories.memory;

import java.util.ArrayList;
import java.util.List;

public record ConversationMemory(List<ConversationTurn> turns) {
    public static final int MAX_TURNS = 12;
    public static final int MAX_TOTAL_CHARACTERS = 24_576;

    public ConversationMemory {
        turns = List.copyOf(turns);
        if (turns.size() > MAX_TURNS || characterCount(turns) > MAX_TOTAL_CHARACTERS) {
            throw new IllegalArgumentException("Conversation memory exceeds its bounds.");
        }
    }

    public static ConversationMemory empty() {
        return new ConversationMemory(List.of());
    }

    public ConversationMemory append(String playerMessage, String npcReply) {
        ArrayList<ConversationTurn> updated = new ArrayList<>(turns);
        updated.add(new ConversationTurn(playerMessage, npcReply));
        while (updated.size() > MAX_TURNS || characterCount(updated) > MAX_TOTAL_CHARACTERS) {
            updated.removeFirst();
        }
        return new ConversationMemory(updated);
    }

    private static int characterCount(List<ConversationTurn> turns) {
        int total = 0;
        for (ConversationTurn turn : turns) {
            total += turn.playerMessage().length() + turn.npcReply().length();
        }
        return total;
    }
}
