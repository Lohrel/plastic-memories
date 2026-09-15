package dev.lohrel.plasticmemories.skill;

import java.util.Objects;

public record SkillSelection(SkillId skill) {
    public SkillSelection {
        Objects.requireNonNull(skill, "skill");
    }
}
