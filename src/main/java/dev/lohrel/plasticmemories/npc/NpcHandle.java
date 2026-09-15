package dev.lohrel.plasticmemories.npc;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;

public interface NpcHandle {
    UUID id();

    Mob entity();

    Container inventory();

    boolean canAssignSkill(ServerPlayer player);

    boolean availableForSkill();
}
