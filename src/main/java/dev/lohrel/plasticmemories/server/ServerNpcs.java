package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.adapter.mca.McaNpcResolver;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import java.util.Optional;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;

/** Turns an entity into an NpcHandle. The only place server code touches an NPC adapter. */
final class ServerNpcs {
    /** How close a player must be to talk to, inspect or ask something of an NPC. */
    static final double INTERACTION_RANGE = 32.0;
    static final double INTERACTION_RANGE_SQUARED = INTERACTION_RANGE * INTERACTION_RANGE;

    private ServerNpcs() {
    }

    /** Whether some adapter recognizes this entity, whatever its state. */
    static boolean isSupported(Entity entity) {
        return McaNpcResolver.resolve(entity).isPresent();
    }

    /** A supported NPC that is alive and adult; empty otherwise. Distance is not checked. */
    static Optional<NpcHandle> usable(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return McaNpcResolver.resolve(entity).filter(handle -> handle.entity().isAlive()
                && !handle.entity().isRemoved()
                && (!(handle.entity() instanceof AgeableMob ageable) || !ageable.isBaby()));
    }
}
