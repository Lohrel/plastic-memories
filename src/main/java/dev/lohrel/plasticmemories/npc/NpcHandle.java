package dev.lohrel.plasticmemories.npc;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;

/** What the mod needs from an NPC, independent of which mod provides it. MCA is the only implementation today. */
public interface NpcHandle {
    UUID id();

    Mob entity();

    Container inventory();

    /** Whether this player is liked enough to ask for a skill. */
    boolean canAssignSkill(ServerPlayer player);

    /** False while the NPC is busy with its own work (for MCA: an assigned job). */
    boolean availableForSkill();
}
