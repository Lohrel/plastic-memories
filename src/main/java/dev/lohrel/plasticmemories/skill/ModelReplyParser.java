package dev.lohrel.plasticmemories.skill;

import java.util.List;

/**
 * Parses the model's "REPLY: ..." / "SKILL: ..." answer. Anything that isn't exactly that shape
 * still shows the text to the player, but the skill becomes NONE.
 */
public final class ModelReplyParser {
    public static final int MAX_DIALOGUE_LENGTH = 2_048;
    public static final int MAX_MODEL_OUTPUT_LENGTH = 4_096;

    private ModelReplyParser() {
    }

    public static ModelReply parse(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return new ModelReply("...", SkillId.NONE);
        }
        String stripped = modelOutput.strip();
        if (stripped.length() > MAX_MODEL_OUTPUT_LENGTH) {
            return new ModelReply(truncateVisible(stripped), SkillId.NONE);
        }

        List<String> nonBlank = stripped.lines().filter(line -> !line.isBlank()).toList();
        List<String> replies = nonBlank.stream().filter(line -> line.startsWith("REPLY: ")).toList();
        List<String> skills = nonBlank.stream().filter(line -> line.startsWith("SKILL: ")).toList();
        boolean protocolShapeValid = nonBlank.size() == 2
                && replies.size() == 1
                && skills.size() == 1
                && nonBlank.get(0).startsWith("REPLY: ")
                && nonBlank.get(1).startsWith("SKILL: ");
        if (protocolShapeValid) {
            String dialogue = replies.getFirst().substring("REPLY: ".length()).strip();
            SkillId skill = parseSkill(skills.getFirst());
            if (!dialogue.isBlank() && dialogue.length() <= MAX_DIALOGUE_LENGTH && skill != null) {
                return new ModelReply(dialogue, skill);
            }
        }

        String visible = nonBlank.stream()
                .filter(line -> !line.startsWith("SKILL:"))
                .map(line -> line.startsWith("REPLY: ") ? line.substring("REPLY: ".length()) : line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("...")
                .strip();
        return new ModelReply(truncateVisible(visible), SkillId.NONE);
    }

    private static SkillId parseSkill(String line) {
        return switch (line) {
            case "SKILL: NONE" -> SkillId.NONE;
            case "SKILL: COOK" -> SkillId.COOK;
            default -> null;
        };
    }

    private static String truncateVisible(String value) {
        String nonBlank = value.isBlank() ? "..." : value;
        return nonBlank.length() <= MAX_DIALOGUE_LENGTH
                ? nonBlank
                : nonBlank.substring(0, MAX_DIALOGUE_LENGTH);
    }
}
