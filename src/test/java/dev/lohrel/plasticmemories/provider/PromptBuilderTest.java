package dev.lohrel.plasticmemories.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import org.junit.jupiter.api.Test;

final class PromptBuilderTest {
    private final ProviderSettings settings = ProviderSettings.create(
            "https://example.com/v1", "test-model", "test-key");
    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void buildsASystemMessageWithTheCharacterProfileAndSkillRules() {
        ProviderRequest request = builder.build(
                settings,
                "John",
                ConversationMemory.empty(),
                "Hello",
                CookAvailability.AVAILABLE,
                NpcProfile.create("Village healer", "Warm", "Red coat", "From the old mine"));

        String system = request.messages().get(0).content();
        assertTrue(system.startsWith("You are John."));
        assertTrue(system.contains("Description: Village healer"));
        assertTrue(system.contains("Personality: Warm"));
        assertTrue(system.contains("Appearance: Red coat"));
        assertTrue(system.contains("Backstory: From the old mine"));
        assertTrue(system.contains("REPLY: <dialogue> and SKILL: <NONE or COOK>"));
        assertTrue(system.contains("COOK availability: AVAILABLE"));
        assertEquals("system", request.messages().get(0).role());
    }

    @Test
    void appendsRememberedHistoryThenThePlayerMessageInOrder() {
        ConversationMemory memory = ConversationMemory.empty()
                .append("First", "First reply")
                .append("Second", "Second reply");

        ProviderRequest request = builder.build(
                settings, "John", memory, "Third", CookAvailability.NO_FOOD, NpcProfile.empty());

        assertEquals(6, request.messages().size());
        assertEquals("user", request.messages().get(1).role());
        assertEquals("First", request.messages().get(1).content());
        assertEquals("assistant", request.messages().get(2).role());
        assertEquals("REPLY: First reply\nSKILL: NONE", request.messages().get(2).content());
        assertEquals("user", request.messages().get(3).role());
        assertEquals("Second", request.messages().get(3).content());
        assertEquals("assistant", request.messages().get(4).role());
        assertEquals("REPLY: Second reply\nSKILL: NONE", request.messages().get(4).content());
        assertEquals("user", request.messages().get(5).role());
        assertEquals("Third", request.messages().get(5).content());
    }

    @Test
    void playerMessageRoleIsUserAndCarriesThePrivateMessage() {
        ProviderRequest request = builder.build(
                settings, "John", ConversationMemory.empty(), "This is private",
                CookAvailability.BUSY, NpcProfile.empty());

        ProviderRequest.Message last = request.messages().get(request.messages().size() - 1);
        assertEquals("user", last.role());
        assertEquals("This is private", last.content());
    }

    @Test
    void rejectsAnOversizedPlayerMessage() {
        String huge = "x".repeat(513);
        assertThrows(IllegalArgumentException.class, () -> builder.build(
                settings, "John", ConversationMemory.empty(), huge,
                CookAvailability.AVAILABLE, NpcProfile.empty()));
    }
}
