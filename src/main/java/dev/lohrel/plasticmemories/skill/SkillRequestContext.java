package dev.lohrel.plasticmemories.skill;

public record SkillRequestContext(
        boolean supportedNpc,
        boolean validNpc,
        boolean busy,
        double distanceSquared) {
}
