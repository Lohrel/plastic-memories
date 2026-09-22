package dev.lohrel.plasticmemories.server;

/** COOK is cancelled if the player or NPC changes dimension. Takes Object so it can be unit-tested. */
final class CookTaskLevelPolicy {
    private CookTaskLevelPolicy() {
    }

    static boolean remainsInOrigin(Object originLevel, Object playerLevel, Object npcLevel) {
        return playerLevel == originLevel && npcLevel == originLevel;
    }
}
