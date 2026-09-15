package dev.lohrel.plasticmemories.server;

public final class NpcProfileAuthorization {
    private NpcProfileAuthorization() {
    }

    public static boolean canEdit(boolean operator, boolean singleplayerOwner) {
        return operator || singleplayerOwner;
    }
}
