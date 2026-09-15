package dev.lohrel.plasticmemories.server;

import java.util.UUID;

interface NpcTask {
    boolean tick();

    boolean belongsTo(UUID playerId);

    void abortForShutdown();
}