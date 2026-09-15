package dev.lohrel.plasticmemories.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SkillSelectionParserTest {
    @Test
    void rejectsMultipleSkillLinesAsAmbiguous() {
        SkillSelection selection = SkillSelectionParser.parse("SKILL: COOK\nSKILL: NONE");

        assertEquals(SkillId.NONE, selection.skill());
    }

    @Test
    void selectsCookWhenAReplyPrecedesTheSkillLine() {
        SkillSelection selection = SkillSelectionParser.parse("REPLY: I will prepare something.\nSKILL: COOK");

        assertEquals(SkillId.COOK, selection.skill());
    }

    @Test
    void selectsCookFromAValidSkillLine() {
        SkillSelection selection = SkillSelectionParser.parse("SKILL: COOK");

        assertEquals(SkillId.COOK, selection.skill());
    }
}
