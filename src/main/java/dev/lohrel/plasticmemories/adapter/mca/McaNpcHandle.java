package dev.lohrel.plasticmemories.adapter.mca;

import dev.lohrel.plasticmemories.npc.NpcHandle;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;

/** {@link NpcHandle} backed by an MCA Reborn villager. */
public final class McaNpcHandle implements NpcHandle {
    private final VillagerEntityMCA villager;

    public McaNpcHandle(VillagerEntityMCA villager) {
        this.villager = villager;
    }

    @Override
    public UUID id() {
        return villager.getUUID();
    }

    @Override
    public Mob entity() {
        return villager;
    }

    @Override
    public Container inventory() {
        return villager.getInventory();
    }

    @Override
    public boolean canAssignSkill(ServerPlayer player) {
        // Any non-negative MCA relationship is enough; only villagers that dislike the player refuse.
        return villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= 0;
    }

    @Override
    public boolean availableForSkill() {
        return villager.getVillagerBrain().getJobAssigner().isEmpty();
    }
}
