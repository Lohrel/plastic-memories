package dev.lohrel.plasticmemories.skill;

import java.util.Objects;

public record ModelReply(String dialogue, SkillId skill) {
    public ModelReply {
        Objects.requireNonNull(dialogue, "dialogue");
        Objects.requireNonNull(skill, "skill");
    }
}
