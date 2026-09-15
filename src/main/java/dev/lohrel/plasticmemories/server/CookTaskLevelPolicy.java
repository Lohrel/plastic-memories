package dev.lohrel.plasticmemories.server;

final class CookTaskLevelPolicy {
    private CookTaskLevelPolicy() {
    }

    static boolean remainsInOrigin(Object originLevel, Object playerLevel, Object npcLevel) {
        return playerLevel == originLevel && npcLevel == originLevel;
    }
}
