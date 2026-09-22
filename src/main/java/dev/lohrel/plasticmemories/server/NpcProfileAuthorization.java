package dev.lohrel.plasticmemories.server;

/** Only operators, or the owner of a singleplayer/LAN world, may edit shared profiles. */
public final class NpcProfileAuthorization {
    private NpcProfileAuthorization() {
    }

    public static boolean canEdit(boolean operator, boolean singleplayerOwner) {
        return operator || singleplayerOwner;
    }
}
