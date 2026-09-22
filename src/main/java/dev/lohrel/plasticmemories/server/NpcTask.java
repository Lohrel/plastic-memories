package dev.lohrel.plasticmemories.server;

import java.util.UUID;

/** A running NPC skill. Ticked on the server thread by {@link ServerSkillTasks}. */
interface NpcTask {
    /** Advances one server tick. Returns true when the task is finished and should be removed. */
    boolean tick();

    boolean belongsTo(UUID playerId);

    /** Stops without sending a result, e.g. when the server stops or the player logs out. */
    void abortForShutdown();
}