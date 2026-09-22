package dev.lohrel.plasticmemories.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.conversation.OutgoingChatRoute;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client entry point: registers the /plasticmemories command and redirects chat during a private conversation. */
@Mod(value = PlasticMemories.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PlasticMemories.MOD_ID, value = Dist.CLIENT)
public final class PlasticMemoriesClient {
    private static final ConversationOrchestrator ORCHESTRATOR = new ConversationOrchestrator();

    public PlasticMemoriesClient() {
    }

    // Registered client-side so the command (and the NPC name in it) is never sent to the server. See ADR 004.
    @SubscribeEvent
    static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("plasticmemories")
                .executes(context -> ORCHESTRATOR.showHelp())
                .then(Commands.literal("help").executes(context -> ORCHESTRATOR.showHelp()))
                .then(Commands.literal("status").executes(context -> ORCHESTRATOR.showStatus()))
                .then(Commands.literal("leave").executes(context -> ORCHESTRATOR.leave()))
                .then(Commands.literal("cook").executes(context -> ORCHESTRATOR.requestCook()))
                .then(Commands.literal("character").executes(context -> ORCHESTRATOR.openCharacterProfile()))
                .then(Commands.literal("provider").executes(context -> ORCHESTRATOR.openProviderSettings()))
                .then(Commands.literal("memory")
                        .then(Commands.literal("status").executes(context -> ORCHESTRATOR.showMemoryStatus()))
                        .then(Commands.literal("clear").executes(context -> ORCHESTRATOR.clearMemory())))
                .then(Commands.literal("lorebook").executes(context -> ORCHESTRATOR.openLorebookLibrary()))
                .then(Commands.literal("persona")
                        .executes(context -> ORCHESTRATOR.openPersonas())
                        .then(Commands.literal("next").executes(context -> ORCHESTRATOR.nextPersona())))
                .then(Commands.argument("npc", StringArgumentType.string())
                        .executes(context -> ORCHESTRATOR.enter(
                                StringArgumentType.getString(context, "npc")))));
    }

    @SubscribeEvent
    static void interceptPrivateChat(ClientChatEvent event) {
        if (ORCHESTRATOR.conversation().route(event.getOriginalMessage()) != OutgoingChatRoute.LOCAL_PRIVATE) {
            return;
        }
        // Cancel before it's sent: the message must never reach server chat.
        event.setCanceled(true);
        ORCHESTRATOR.sendPrivateMessage(event.getOriginalMessage());
    }

    @SubscribeEvent
    static void clearConversationOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ORCHESTRATOR.clearOnDisconnect();
    }
}
