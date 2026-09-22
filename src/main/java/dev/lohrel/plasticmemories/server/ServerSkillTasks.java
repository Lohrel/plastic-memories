package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.network.SkillResultCode;
import dev.lohrel.plasticmemories.network.SkillResultPayload;
import dev.lohrel.plasticmemories.npc.NpcHandle;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PlasticMemories.MOD_ID)
/** Holds the running skill task for each NPC (at most one) and ticks them every server tick. */
public final class ServerSkillTasks {
    private static final Map<UUID, NpcTask> ACTIVE_TASKS = new HashMap<>();

    private ServerSkillTasks() {
    }

    public static boolean isBusy(UUID npcId) {
        return ACTIVE_TASKS.containsKey(npcId);
    }

    public static void startCook(ServerPlayer player, NpcHandle npc, long requestId) {
        CookTask.StartResult start = CookTask.start(player, npc, requestId);
        if (!start.started()) {
            PacketDistributor.sendToPlayer(
                    player, new SkillResultPayload(npc.id(), requestId, start.failure()));
            return;
        }
        ACTIVE_TASKS.put(npc.id(), start.task());
        PacketDistributor.sendToPlayer(
                player, new SkillResultPayload(npc.id(), requestId, SkillResultCode.STARTED));
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, NpcTask>> iterator = ACTIVE_TASKS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().tick()) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        Iterator<Map.Entry<UUID, NpcTask>> iterator = ACTIVE_TASKS.entrySet().iterator();
        while (iterator.hasNext()) {
            NpcTask task = iterator.next().getValue();
            if (task.belongsTo(playerId)) {
                task.abortForShutdown();
                iterator.remove();
            }
        }
        SkillServerPayloadHandler.forgetPlayer(playerId);
        NpcCapabilityServerPayloadHandler.forgetPlayer(playerId);
        NpcProfileServerPayloadHandler.forgetPlayer(playerId);
    }

    @SubscribeEvent
    static void clear(ServerStoppingEvent event) {
        ACTIVE_TASKS.values().forEach(NpcTask::abortForShutdown);
        ACTIVE_TASKS.clear();
        SkillServerPayloadHandler.reset();
        NpcCapabilityServerPayloadHandler.reset();
        NpcProfileServerPayloadHandler.reset();
    }
}
