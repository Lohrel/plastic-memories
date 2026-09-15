package dev.lohrel.plasticmemories.network;

final class SkillResultMessages {
    private SkillResultMessages() {
    }

    static String message(SkillResultCode result) {
        return switch (result) {
            case STARTED -> "[Plastic Memories] COOK started: the NPC is retrieving up to four food items.";
            case SUCCESS -> "[Plastic Memories] COOK succeeded: food was delivered.";
            case NO_FOOD -> "[Plastic Memories] COOK failed: the NPC and nearby containers have no food.";
            case NO_PATH -> "[Plastic Memories] COOK failed: the NPC could not reach its destination.";
            case NPC_BUSY -> "[Plastic Memories] COOK rejected: the NPC is busy.";
            case RATE_LIMITED -> "[Plastic Memories] COOK rejected: wait before requesting another task.";
            case OUT_OF_RANGE -> "[Plastic Memories] COOK rejected: the NPC is too far away.";
            case INVALID_NPC -> "[Plastic Memories] COOK rejected: the NPC is unavailable.";
            case UNSUPPORTED_NPC -> "[Plastic Memories] COOK rejected: that entity is unsupported.";
            case UNSUPPORTED_SKILL -> "[Plastic Memories] The requested skill is unsupported.";
            case INVALID_REQUEST -> "[Plastic Memories] Malformed skill request rejected.";
            case REPLAYED -> "[Plastic Memories] Duplicate skill request rejected.";
            case INVENTORY_FULL -> "[Plastic Memories] COOK failed: your inventory is full.";
            case PERMISSION_DENIED -> "[Plastic Memories] COOK rejected: permission denied.";
            case INTERRUPTED -> "[Plastic Memories] COOK was interrupted.";
            case NO_CONTAINER -> "[Plastic Memories] COOK failed: no accessible food container was found nearby.";
            case NPC_INVENTORY_FULL -> "[Plastic Memories] COOK failed: the NPC has no inventory space to carry food.";
        };
    }
}
