package dev.lohrel.plasticmemories.adapter.mca;

import dev.lohrel.plasticmemories.npc.NpcHandle;
import java.util.Optional;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;

public final class McaNpcResolver {
    private McaNpcResolver() {
    }

    public static Optional<NpcHandle> resolve(Entity entity) {
        return entity instanceof VillagerEntityMCA villager
                ? Optional.of(new McaNpcHandle(villager))
                : Optional.empty();
    }
}
