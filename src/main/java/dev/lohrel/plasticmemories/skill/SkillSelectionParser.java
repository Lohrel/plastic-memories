package dev.lohrel.plasticmemories.skill;

public final class SkillSelectionParser {
    private SkillSelectionParser() {
    }

    public static SkillSelection parse(String modelOutput) {
        var skillLines = modelOutput.lines()
                .filter(line -> line.startsWith("SKILL:"))
                .toList();
        if (skillLines.size() != 1) {
            return new SkillSelection(SkillId.NONE);
        }
        return new SkillSelection("SKILL: COOK".equals(skillLines.getFirst()) ? SkillId.COOK : SkillId.NONE);
    }
}
