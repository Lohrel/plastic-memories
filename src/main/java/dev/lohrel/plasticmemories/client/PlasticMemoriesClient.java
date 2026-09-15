package dev.lohrel.plasticmemories.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.conversation.ConversationState;
import dev.lohrel.plasticmemories.conversation.NpcCandidate;
import dev.lohrel.plasticmemories.conversation.NpcTargetResolver;
import dev.lohrel.plasticmemories.conversation.OutgoingChatRoute;
import dev.lohrel.plasticmemories.memory.ConversationMemoryKey;
import dev.lohrel.plasticmemories.memory.ConversationMemoryStore;
import dev.lohrel.plasticmemories.network.NpcCapabilityClientInbox;
import dev.lohrel.plasticmemories.network.NpcCapabilityRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileClientInbox;
import dev.lohrel.plasticmemories.network.NpcProfileRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileResponse;
import dev.lohrel.plasticmemories.network.NpcProfileResultCode;
import dev.lohrel.plasticmemories.network.NpcProfileUpdatePayload;
import dev.lohrel.plasticmemories.network.SkillRequestPayload;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import dev.lohrel.plasticmemories.provider.AiProvider;
import dev.lohrel.plasticmemories.provider.OpenAiCompatibleProvider;
import dev.lohrel.plasticmemories.provider.PromptBuilder;
import dev.lohrel.plasticmemories.provider.ProviderSettingsStore;
import dev.lohrel.plasticmemories.skill.ModelReplyParser;
import dev.lohrel.plasticmemories.skill.SkillId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = PlasticMemories.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PlasticMemories.MOD_ID, value = Dist.CLIENT)
public final class PlasticMemoriesClient {
    private static final double TARGET_RANGE = 32.0;
    private static final ConversationState CONVERSATION = new ConversationState();
    private static final AiProvider PROVIDER = new OpenAiCompatibleProvider(java.time.Duration.ofSeconds(30));
    private static final PromptBuilder PROMPT_BUILDER = new PromptBuilder();
    private static final NpcCapabilityClientInbox CAPABILITY_INBOX = NpcCapabilityClientInbox.shared();
    private static final NpcProfileClientInbox PROFILE_INBOX = NpcProfileClientInbox.shared();
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(1);
    private static boolean providerRequestPending;

    public PlasticMemoriesClient() {
    }

    @SubscribeEvent
    static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("plasticmemories")
                .executes(context -> showHelp())
                .then(Commands.literal("help").executes(context -> showHelp()))
                .then(Commands.literal("status").executes(context -> showStatus()))
                .then(Commands.literal("leave").executes(context -> leaveConversation()))
                .then(Commands.literal("cook").executes(context -> requestCook()))
                .then(Commands.literal("character").executes(context -> openCharacterProfile()))
                .then(Commands.literal("provider").executes(context -> openProviderSettings()))
                .then(Commands.literal("memory")
                        .then(Commands.literal("status").executes(context -> showMemoryStatus()))
                        .then(Commands.literal("clear").executes(context -> clearMemory())))
                .then(Commands.argument("npc", StringArgumentType.string())
                        .executes(context -> enterConversation(StringArgumentType.getString(context, "npc")))));
    }

    @SubscribeEvent
    static void interceptPrivateChat(ClientChatEvent event) {
        if (CONVERSATION.route(event.getOriginalMessage()) != OutgoingChatRoute.LOCAL_PRIVATE) {
            return;
        }

        event.setCanceled(true);
        var target = CONVERSATION.target().orElseThrow();
        String privateMessage = event.getOriginalMessage();
        showLocal(Component.literal("[You -> " + target.name() + "] ").append(privateMessage));
        if (providerRequestPending) {
            showLocal(Component.literal("[Plastic Memories] Wait for the current reply before sending another message."));
            return;
        }

        var settings = providerSettingsStore().load();
        if (settings.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Configure a provider with /plasticmemories provider."));
            return;
        }
        var memoryKey = currentMemoryKey();
        if (memoryKey.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Private memory is unavailable in this world."));
            return;
        }

        ConversationMemoryStore memoryStore = memoryStore();
        var memory = memoryStore.load(memoryKey.orElseThrow());
        providerRequestPending = true;
        showLocal(Component.literal("[Plastic Memories] " + target.name() + " is thinking..."));
        long capabilityRequestId = NEXT_REQUEST_ID.getAndIncrement();
        long profileRequestId = NEXT_REQUEST_ID.getAndIncrement();
        var capability = CAPABILITY_INBOX.expect(target.npcId(), capabilityRequestId)
                .completeOnTimeout(CookAvailability.BUSY, 3, TimeUnit.SECONDS);
        var profile = PROFILE_INBOX.expect(target.npcId(), profileRequestId)
                .completeOnTimeout(
                        new NpcProfileResponse(NpcProfileResultCode.RATE_LIMITED, NpcProfile.empty(), false),
                        3,
                        TimeUnit.SECONDS);
        PacketDistributor.sendToServer(new NpcCapabilityRequestPayload(target.npcId(), capabilityRequestId));
        PacketDistributor.sendToServer(new NpcProfileRequestPayload(target.npcId(), profileRequestId));
        capability.thenCombine(profile, ProviderContext::new)
                .thenCompose(providerContext -> {
                    var providerRequest = PROMPT_BUILDER.build(
                            settings.orElseThrow(),
                            target.name(),
                            memory,
                            privateMessage,
                            providerContext.availability(),
                            providerContext.profileResponse().result() == NpcProfileResultCode.SUCCESS
                                    ? providerContext.profileResponse().profile()
                                    : NpcProfile.empty());
                    return PROVIDER.reply(providerRequest);
                })
                .whenComplete((reply, error) -> Minecraft.getInstance().execute(() -> {
                    providerRequestPending = false;
                    boolean targetStillActive = CONVERSATION.target()
                            .map(active -> active.npcId().equals(target.npcId()))
                            .orElse(false);
                    if (error == null) {
                        var modelReply = ModelReplyParser.parse(reply);
                        try {
                            memoryStore.save(
                                    memoryKey.orElseThrow(),
                                    memory.append(privateMessage, modelReply.dialogue()));
                        } catch (IOException exception) {
                            if (targetStillActive) {
                                showLocal(Component.literal("[Plastic Memories] Reply received, but private memory could not be saved."));
                            }
                        }
                        if (targetStillActive) {
                            showLocal(Component.literal("[" + target.name() + " -> You] ").append(modelReply.dialogue()));
                            if (modelReply.skill() == SkillId.COOK) {
                                sendCookRequest(target.npcId());
                            }
                        }
                    } else if (targetStillActive) {
                        showLocal(Component.literal("[Plastic Memories] "
                                + OpenAiCompatibleProvider.safeFailureMessage(error)));
                    }
                }));
    }

    @SubscribeEvent
    static void clearConversationOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        CONVERSATION.leave();
        CAPABILITY_INBOX.clear();
        PROFILE_INBOX.clear();
    }

    private static int showHelp() {
        showLocal(Component.literal("[Plastic Memories] Commands:"));
        showLocal(Component.literal("/plasticmemories \"<NPC name>\" - start a private conversation"));
        showLocal(Component.literal("/plasticmemories leave - return to server chat"));
        showLocal(Component.literal("/plasticmemories cook - directly request the selected NPC's COOK skill"));
        showLocal(Component.literal("/plasticmemories character - view or edit the selected NPC's shared profile"));
        showLocal(Component.literal("/plasticmemories provider - configure the AI provider"));
        showLocal(Component.literal("/plasticmemories status - show private conversation status"));
        showLocal(Component.literal("/plasticmemories memory status|clear - manage private memory"));
        return 1;
    }

    private static int showStatus() {
        String targetName = CONVERSATION.target().map(target -> target.name()).orElse("none");
        boolean providerConfigured = providerSettingsStore().load().isPresent();
        int rememberedTurns = currentMemoryKey()
                .map(key -> memoryStore().load(key).turns().size())
                .orElse(0);
        showLocal(Component.literal("[Plastic Memories] NPC: " + targetName
                + " | provider: " + (providerConfigured ? "configured" : "not configured")
                + " | remembered turns: " + rememberedTurns
                + " | request pending: " + providerRequestPending));
        return 1;
    }

    private static int enterConversation(String requestedName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            showLocal(Component.literal("[Plastic Memories] Join a world before selecting an NPC."));
            return 0;
        }

        List<NpcCandidate> candidates = minecraft.level
                .getEntities(minecraft.player, minecraft.player.getBoundingBox().inflate(TARGET_RANGE))
                .stream()
                .filter(PlasticMemoriesClient::isMcaEntity)
                .map(entity -> new NpcCandidate(
                        entity.getUUID(),
                        entity.getName().getString(),
                        "mca",
                        minecraft.player.distanceToSqr(entity)))
                .toList();

        var resolution = NpcTargetResolver.resolve(requestedName, candidates, TARGET_RANGE);
        return switch (resolution.status()) {
            case FOUND -> {
                var target = resolution.target().orElseThrow();
                CONVERSATION.enter(target);
                int rememberedTurns = currentMemoryKey()
                        .map(key -> memoryStore().load(key).turns().size())
                        .orElse(0);
                showLocal(Component.literal("[Plastic Memories] Private conversation with " + target.name()
                        + " started with " + rememberedTurns
                        + " remembered turns. Use /plasticmemories leave to exit."));
                yield 1;
            }
            case NOT_FOUND -> {
                showLocal(Component.literal("[Plastic Memories] No nearby MCA NPC named \"" + requestedName + "\"."));
                yield 0;
            }
            case AMBIGUOUS -> {
                showLocal(Component.literal("[Plastic Memories] More than one nearby MCA NPC is named \""
                        + requestedName + "\"."));
                yield 0;
            }
        };
    }

    private static int requestCook() {
        var target = CONVERSATION.target();
        if (target.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC before requesting COOK."));
            return 0;
        }
        sendCookRequest(target.orElseThrow().npcId());
        return 1;
    }

    private static int openCharacterProfile() {
        var target = CONVERSATION.target();
        if (target.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC before opening its character profile."));
            return 0;
        }
        var selected = target.orElseThrow();
        long requestId = NEXT_REQUEST_ID.getAndIncrement();
        var response = PROFILE_INBOX.expect(selected.npcId(), requestId).orTimeout(3, TimeUnit.SECONDS);
        PacketDistributor.sendToServer(new NpcProfileRequestPayload(selected.npcId(), requestId));
        response.whenComplete((profileResponse, error) -> Minecraft.getInstance().execute(() -> {
            boolean targetStillActive = CONVERSATION.target()
                    .map(active -> active.npcId().equals(selected.npcId()))
                    .orElse(false);
            if (!targetStillActive) {
                return;
            }
            if (error != null) {
                showLocal(Component.literal("[Plastic Memories] Character profile request failed."));
                return;
            }
            if (profileResponse.result() != NpcProfileResultCode.SUCCESS) {
                showLocal(Component.literal("[Plastic Memories] " + profileResultMessage(profileResponse.result())));
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new NpcProfileScreen(
                    minecraft.screen,
                    selected.name(),
                    profileResponse.profile(),
                    profileResponse.canEdit(),
                    (screen, profile) -> updateCharacterProfile(selected.npcId(), screen, profile)));
        }));
        return 1;
    }

    private static void updateCharacterProfile(
            java.util.UUID npcId, NpcProfileScreen screen, NpcProfile profile) {
        long requestId = NEXT_REQUEST_ID.getAndIncrement();
        var response = PROFILE_INBOX.expect(npcId, requestId).orTimeout(3, TimeUnit.SECONDS);
        PacketDistributor.sendToServer(new NpcProfileUpdatePayload(npcId, requestId, profile));
        response.whenComplete((profileResponse, error) -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen != screen) {
                return;
            }
            if (error == null) {
                screen.handleResponse(profileResponse);
            } else {
                screen.handleFailure();
            }
        }));
    }

    private static void sendCookRequest(java.util.UUID npcId) {
        long requestId = NEXT_REQUEST_ID.getAndIncrement();
        PacketDistributor.sendToServer(new SkillRequestPayload(npcId, requestId, SkillId.COOK.name()));
    }

    private static int leaveConversation() {
        if (CONVERSATION.target().isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] No private conversation is active."));
            return 0;
        }
        CONVERSATION.leave();
        showLocal(Component.literal("[Plastic Memories] Private conversation ended. Messages will use server chat."));
        return 1;
    }

    private static int showMemoryStatus() {
        var key = currentMemoryKey();
        if (key.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC in a world first."));
            return 0;
        }
        int turns = memoryStore().load(key.orElseThrow()).turns().size();
        showLocal(Component.literal("[Plastic Memories] Stored private memory: " + turns + " conversation turns."));
        return 1;
    }

    private static int clearMemory() {
        var key = currentMemoryKey();
        if (key.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC in a world first."));
            return 0;
        }
        try {
            memoryStore().clear(key.orElseThrow());
            showLocal(Component.literal("[Plastic Memories] Private memory for this NPC was cleared."));
            return 1;
        } catch (IOException exception) {
            showLocal(Component.literal("[Plastic Memories] Private memory could not be cleared."));
            return 0;
        }
    }

    private static Optional<ConversationMemoryKey> currentMemoryKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || CONVERSATION.target().isEmpty()) {
            return Optional.empty();
        }
        Optional<String> worldIdentity = currentWorldIdentity(minecraft);
        return worldIdentity.map(identity -> new ConversationMemoryKey(
                identity,
                minecraft.player.getUUID(),
                CONVERSATION.target().orElseThrow().npcId()));
    }

    private static Optional<String> currentWorldIdentity(Minecraft minecraft) {
        var localServer = minecraft.getSingleplayerServer();
        if (localServer != null) {
            Path worldPath = localServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return Optional.of("singleplayer:" + worldPath);
        }
        var remoteServer = minecraft.getCurrentServer();
        if (remoteServer != null && remoteServer.ip != null && !remoteServer.ip.isBlank()) {
            return Optional.of("multiplayer:" + remoteServer.ip);
        }
        return Optional.empty();
    }

    private static ConversationMemoryStore memoryStore() {
        Path path = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve(PlasticMemories.MOD_ID)
                .resolve("private-memories");
        return new ConversationMemoryStore(path);
    }

    private static int openProviderSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        ProviderSettingsStore store = providerSettingsStore();
        minecraft.setScreen(new ProviderSettingsScreen(null, store, store.load()));
        return 1;
    }

    private static ProviderSettingsStore providerSettingsStore() {
        Path path = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve(PlasticMemories.MOD_ID)
                .resolve("provider.json");
        return new ProviderSettingsStore(path);
    }

    private static boolean isMcaEntity(Entity entity) {
        return "mca".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    private static void showLocal(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(message);
        }
    }

    private static String profileResultMessage(NpcProfileResultCode result) {
        return switch (result) {
            case SUCCESS -> "Character profile loaded.";
            case INVALID_REQUEST -> "Character profile request was malformed.";
            case INVALID_NPC -> "The selected NPC is unavailable.";
            case OUT_OF_RANGE -> "The selected NPC is too far away.";
            case PERMISSION_DENIED -> "Only server operators or the LAN host can edit this profile.";
            case RATE_LIMITED -> "Wait before requesting the character profile again.";
            case REPLAYED -> "Duplicate character profile request rejected.";
        };
    }

    private record ProviderContext(CookAvailability availability, NpcProfileResponse profileResponse) {
    }
}
