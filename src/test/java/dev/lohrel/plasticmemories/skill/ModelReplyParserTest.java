package dev.lohrel.plasticmemories.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModelReplyParserTest {
    @Test
    void parsesBoundedDialogueAndCookSelection() {
        ModelReply reply = ModelReplyParser.parse("REPLY: I'll bring you something to eat.\nSKILL: COOK");

        assertEquals("I'll bring you something to eat.", reply.dialogue());
        assertEquals(SkillId.COOK, reply.skill());
    }

    @Test
    void acceptsExplicitNone() {
        ModelReply reply = ModelReplyParser.parse("REPLY: I am listening.\nSKILL: NONE");

        assertEquals("I am listening.", reply.dialogue());
        assertEquals(SkillId.NONE, reply.skill());
    }

    @Test
    void malformedOrAmbiguousProtocolCannotInvokeSkill() {
        assertEquals(SkillId.NONE, ModelReplyParser.parse("REPLY: Sure.\nSKILL: COOK\nSKILL: NONE").skill());
        assertEquals(SkillId.NONE, ModelReplyParser.parse("SKILL: COOK\nREPLY: Sure.").skill());
        assertEquals(SkillId.NONE, ModelReplyParser.parse("REPLY: Sure.\nSKILL: MINE").skill());
        assertEquals(SkillId.NONE, ModelReplyParser.parse("SKILL: COOK").skill());
    }

    @Test
    void legacyPlainDialogueRemainsVisibleButCannotInvokeSkill() {
        ModelReply reply = ModelReplyParser.parse("Hello there.");

        assertEquals("Hello there.", reply.dialogue());
        assertEquals(SkillId.NONE, reply.skill());
    }

    @Test
    void oversizedOutputCannotInvokeSkill() {
        String output = "REPLY: " + "x".repeat(2_049) + "\nSKILL: COOK";

        assertEquals(SkillId.NONE, ModelReplyParser.parse(output).skill());
    }
}
