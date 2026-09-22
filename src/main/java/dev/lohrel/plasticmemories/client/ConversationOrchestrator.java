package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.card.CardBindingStore;
import dev.lohrel.plasticmemories.card.CharacterCardFolder;
import dev.lohrel.plasticmemories.card.CharacterCards;
import dev.lohrel.plasticmemories.conversation.ConversationState;
import dev.lohrel.plasticmemories.conversation.ConversationTarget;
import dev.lohrel.plasticmemories.conversation.NpcCandidate;
import dev.lohrel.plasticmemories.conversation.NpcTargetResolver;
import dev.lohrel.plasticmemories.lorebook.ClientLorebookInbox;
import dev.lohrel.plasticmemories.lorebook.ClientLorebookLibraryStore;
import dev.lohrel.plasticmemories.lorebook.ClientLorebookRequestContextLoader;
import dev.lohrel.plasticmemories.lorebook.ImportedPromptContext;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import dev.lohrel.plasticmemories.memory.ConversationMemory;
import dev.lohrel.plasticmemories.memory.ConversationMemoryKey;
import dev.lohrel.plasticmemories.memory.ConversationMemoryStore;
import dev.lohrel.plasticmemories.network.NpcCapabilityRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileRequestPayload;
import dev.lohrel.plasticmemories.network.NpcProfileResponse;
import dev.lohrel.plasticmemories.network.NpcProfileResultCode;
import dev.lohrel.plasticmemories.network.NpcProfileUpdatePayload;
import dev.lohrel.plasticmemories.network.PendingNpcRequests;
import dev.lohrel.plasticmemories.network.SkillRequestPayload;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import dev.lohrel.plasticmemories.persona.PersonaStore;
import dev.lohrel.plasticmemories.provider.AiProvider;
import dev.lohrel.plasticmemories.provider.OpenAiCompatibleProvider;
import dev.lohrel.plasticmemories.provider.PromptBuilder;
import dev.lohrel.plasticmemories.provider.PromptPersona;
import dev.lohrel.plasticmemories.provider.ProviderSettingsStore;
import dev.lohrel.plasticmemories.skill.ModelReplyParser;
import dev.lohrel.plasticmemories.skill.SkillId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side hub: runs every /plasticmemories command and the full private-message flow
 * (ask server for context -> build prompt -> call provider -> show reply -> save memory -> maybe request a skill).
 */
public final class ConversationOrchestrator {
    static final double TARGET_RANGE = 32.0;

    private final ConversationState conversation = new ConversationState();
    private final AiProvider provider;
    private final PromptBuilder promptBuilder;
    private final PendingNpcRequests<CookAvailability> capabilityInbox;
    private final PendingNpcRequests<NpcProfileResponse> profileInbox;
    private final AtomicLong nextRequestId = new AtomicLong(1);
    private ClientLorebookLibraryStore lorebookLibrary;
    private ClientLorebookRequestContextLoader lorebookContextLoader;
    // One instance, so the folder's parsed-card cache survives between messages.
    private CharacterCards characterCards;
    private boolean requestPending;

    public ConversationOrchestrator() {
        this.provider = new OpenAiCompatibleProvider(Duration.ofSeconds(30));
        this.promptBuilder = new PromptBuilder();
        this.capabilityInbox = PendingNpcRequests.COOK_AVAILABILITY;
        this.profileInbox = PendingNpcRequests.PROFILES;
    }

    public ConversationOrchestrator(
            AiProvider provider,
            PromptBuilder promptBuilder,
            PendingNpcRequests<CookAvailability> capabilityInbox,
            PendingNpcRequests<NpcProfileResponse> profileInbox) {
        this.provider = provider;
        this.promptBuilder = promptBuilder;
        this.capabilityInbox = capabilityInbox;
        this.profileInbox = profileInbox;
    }

    public ConversationState conversation() {
        return conversation;
    }

    public boolean requestPending() {
        return requestPending;
    }

    public void clearOnDisconnect() {
        conversation.leave();
        capabilityInbox.clear();
        profileInbox.clear();
        if (lorebookContextLoader != null) {
            lorebookContextLoader.clear();
        }
    }

    public int enter(String requestedName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            showLocal(Component.literal("[Plastic Memories] Join a world before selecting an NPC."));
            return 0;
        }

        List<NpcCandidate> candidates = minecraft.level
                .getEntities(minecraft.player, minecraft.player.getBoundingBox().inflate(TARGET_RANGE))
                .stream()
                .filter(this::isMcaEntity)
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
                conversation.enter(target);
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

    public void sendPrivateMessage(String message) {
        var target = conversation.target();
        if (target.isEmpty()) {
            return;
        }
        var convTarget = target.orElseThrow();
        showLocal(Component.literal("[You -> " + convTarget.name() + "] ").append(message));
        if (requestPending) {
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

        ConversationMemoryStore store = memoryStore();
        var memory = store.load(memoryKey.orElseThrow());
        requestPending = true;
        showLocal(Component.literal("[Plastic Memories] " + convTarget.name() + " is thinking..."));
        // Ask the server for COOK availability and the shared profile in parallel. If the server is slow,
        // continue after 3 s with safe defaults (BUSY, empty profile) instead of blocking the chat.
        long capabilityRequestId = nextRequestId.getAndIncrement();
        long profileRequestId = nextRequestId.getAndIncrement();
        var capability = capabilityInbox.expect(convTarget.npcId(), capabilityRequestId)
                .completeOnTimeout(CookAvailability.BUSY, 3, TimeUnit.SECONDS);
        var profile = profileInbox.expect(convTarget.npcId(), profileRequestId)
                .completeOnTimeout(
                        new NpcProfileResponse(NpcProfileResultCode.RATE_LIMITED, NpcProfile.empty(), false),
                        3,
                        TimeUnit.SECONDS);
        PacketDistributor.sendToServer(new NpcCapabilityRequestPayload(convTarget.npcId(), capabilityRequestId));
        PacketDistributor.sendToServer(new NpcProfileRequestPayload(convTarget.npcId(), profileRequestId));
        PromptPersona persona = currentPersona();
        ImportedPromptContext importedLore;
        try {
            importedLore = loadImportedLorebookContext(memoryKey.orElseThrow(), memory, message);
        } catch (RuntimeException exception) {
            // Without this, requestPending would stay true and every later message would be refused.
            requestPending = false;
            showLocal(Component.literal("[Plastic Memories] Could not load this NPC's card or lorebooks."));
            return;
        }
        capability.thenCombine(profile, ProviderContext::new)
                .thenCompose(providerContext -> {
                    var providerRequest = promptBuilder.build(
                            settings.orElseThrow(),
                            convTarget.name(),
                            memory,
                            message,
                            providerContext.availability(),
                            providerContext.profileResponse().result() == NpcProfileResultCode.SUCCESS
                                    ? providerContext.profileResponse().profile()
                                    : NpcProfile.empty(),
                            importedLore,
                            persona);
                    return provider.reply(providerRequest);
                })
                // Back to the main thread: chat, memory and packets must not be touched from the HTTP thread.
                .whenComplete((reply, error) -> Minecraft.getInstance().execute(() -> {
                    requestPending = false;
                    // The player may have left or switched NPC while waiting. Still save the memory, but stay quiet.
                    boolean targetStillActive = conversation.target()
                            .map(active -> active.npcId().equals(convTarget.npcId()))
                            .orElse(false);
                    if (error == null) {
                        var modelReply = ModelReplyParser.parse(reply);
                        try {
                            store.save(
                                    memoryKey.orElseThrow(),
                                    memory.append(message, modelReply.dialogue()));
                        } catch (IOException exception) {
                            if (targetStillActive) {
                                showLocal(Component.literal("[Plastic Memories] Reply received, but private memory could not be saved."));
                            }
                        }
                        if (targetStillActive) {
                            showLocal(Component.literal("[" + convTarget.name() + " -> You] ").append(modelReply.dialogue()));
                            if (modelReply.skill() == SkillId.COOK) {
                                sendCookRequest(convTarget.npcId());
                            }
                        }
                    } else if (targetStillActive) {
                        showLocal(Component.literal("[Plastic Memories] "
                                + OpenAiCompatibleProvider.safeFailureMessage(error)));
                    }
                }));
    }

    public int leave() {
        if (conversation.target().isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] No private conversation is active."));
            return 0;
        }
        currentLorebookBindingKey().ifPresent(key -> {
            if (lorebookContextLoader != null) {
                lorebookContextLoader.clear(key);
            }
        });
        conversation.leave();
        showLocal(Component.literal("[Plastic Memories] Private conversation ended. Messages will use server chat."));
        return 1;
    }

    public int showHelp() {
        showLocal(Component.literal("[Plastic Memories] Commands:"));
        showLocal(Component.literal("/plasticmemories \"<NPC name>\" - start a private conversation"));
        showLocal(Component.literal("/plasticmemories leave - return to server chat"));
        showLocal(Component.literal("/plasticmemories cook - directly request the selected NPC's COOK skill"));
        showLocal(Component.literal("/plasticmemories character - view or edit the selected NPC's shared profile"));
        showLocal(Component.literal("/plasticmemories provider - configure the AI provider"));
        showLocal(Component.literal("/plasticmemories status - show private conversation status"));
        showLocal(Component.literal("/plasticmemories memory status|clear - manage private memory"));
        showLocal(Component.literal("/plasticmemories lorebook - open the client-only lorebook library and inbox."));
        showLocal(Component.literal("/plasticmemories persona - manage your personas; persona next - switch to the next one"));
        return 1;
    }

    public int showStatus() {
        String targetName = conversation.target().map(ConversationTarget::name).orElse("none");
        boolean providerConfigured = providerSettingsStore().load().isPresent();
        int rememberedTurns = currentMemoryKey()
                .map(key -> memoryStore().load(key).turns().size())
                .orElse(0);
        showLocal(Component.literal("[Plastic Memories] NPC: " + targetName
                + " | persona: " + currentPersona().name()
                + " | provider: " + (providerConfigured ? "configured" : "not configured")
                + " | remembered turns: " + rememberedTurns
                + " | request pending: " + requestPending));
        return 1;
    }

    public int requestCook() {
        var target = conversation.target();
        if (target.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC before requesting COOK."));
            return 0;
        }
        sendCookRequest(target.orElseThrow().npcId());
        return 1;
    }

    public int openCharacterProfile() {
        var target = conversation.target();
        if (target.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC before opening its character profile."));
            return 0;
        }
        var selected = target.orElseThrow();
        long requestId = nextRequestId.getAndIncrement();
        var response = profileInbox.expect(selected.npcId(), requestId).orTimeout(3, TimeUnit.SECONDS);
        PacketDistributor.sendToServer(new NpcProfileRequestPayload(selected.npcId(), requestId));
        response.whenComplete((profileResponse, error) -> Minecraft.getInstance().execute(() -> {
            boolean targetStillActive = conversation.target()
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
                    (screen, profile) -> updateCharacterProfile(selected.npcId(), screen, profile),
                    currentLorebookBindingKey().map(key -> profileScreen ->
                            new CardEditorScreen(profileScreen, characterCards(), key, selected.name()))));
        }));
        return 1;
    }

    public int openProviderSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ProviderSettingsScreen(null, providerSettingsStore(), providerSettingsStore().load()));
        return 1;
    }

    public int showMemoryStatus() {
        var key = currentMemoryKey();
        if (key.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC in a world first."));
            return 0;
        }
        int turns = memoryStore().load(key.orElseThrow()).turns().size();
        showLocal(Component.literal("[Plastic Memories] Stored private memory: " + turns + " conversation turns."));
        return 1;
    }

    public int clearMemory() {
        var key = currentMemoryKey();
        if (key.isEmpty()) {
            showLocal(Component.literal("[Plastic Memories] Select an NPC in a world first."));
            return 0;
        }
        try {
            ConversationMemoryKey memoryKey = key.orElseThrow();
            memoryStore().clear(memoryKey);
            if (lorebookContextLoader != null) {
                lorebookContextLoader.clear(new LocalLorebookBindingKey(
                        memoryKey.worldIdentity(), memoryKey.playerId(), memoryKey.npcId()));
            }
            showLocal(Component.literal("[Plastic Memories] Private memory for this NPC was cleared."));
            return 1;
        } catch (IOException exception) {
            showLocal(Component.literal("[Plastic Memories] Private memory could not be cleared."));
            return 0;
        }
    }

    public int openPersonas() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PersonaScreen(minecraft.screen, personaStore(), currentLorebookBindingKey(),
                conversation.target().map(ConversationTarget::name)));
        return 1;
    }

    public int nextPersona() {
        try {
            var next = personaStore().cycleActive();
            if (next.isEmpty()) {
                showLocal(Component.literal("[Plastic Memories] No saved personas. Create one with /plasticmemories persona."));
                return 0;
            }
            String message = "[Plastic Memories] Persona: " + next.orElseThrow().name();
            // A lock on the current NPC still wins, so say so rather than letting the switch look broken.
            var locked = currentLorebookBindingKey().flatMap(personaStore()::lockedFor);
            if (locked.isPresent()) {
                message += " (" + conversation.target().map(ConversationTarget::name).orElse("this NPC")
                        + " still sees you as " + locked.orElseThrow().name() + ")";
            }
            showLocal(Component.literal(message));
            return 1;
        } catch (IOException exception) {
            showLocal(Component.literal("[Plastic Memories] Could not save the persona change."));
            return 0;
        }
    }

    /** The persona for the current NPC (its lock, else the active one), or the player's own name. */
    private PromptPersona currentPersona() {
        var persona = currentLorebookBindingKey().flatMap(personaStore()::personaFor).or(personaStore()::active);
        if (persona.isPresent()) {
            return new PromptPersona(persona.orElseThrow().name(), persona.orElseThrow().description());
        }
        var player = Minecraft.getInstance().player;
        return player == null ? PromptPersona.DEFAULT : new PromptPersona(player.getName().getString(), "");
    }

    private PersonaStore personaStore() {
        return new PersonaStore(configDirectory().resolve("personas.json"));
    }

    public int openLorebookLibrary() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new LorebookLibraryScreen(
                minecraft.screen, lorebookLibraryStore(), lorebookInbox(), currentLorebookBindingKey()));
        return 1;
    }

    private Optional<LocalLorebookBindingKey> currentLorebookBindingKey() {
        return currentMemoryKey().map(key -> new LocalLorebookBindingKey(
                key.worldIdentity(), key.playerId(), key.npcId()));
    }

    private ImportedPromptContext loadImportedLorebookContext(
            ConversationMemoryKey key, ConversationMemory memory, String currentMessage) {
        LocalLorebookBindingKey bindingKey = new LocalLorebookBindingKey(key.worldIdentity(), key.playerId(), key.npcId());
        return lorebookRequestContextLoader().load(bindingKey, memory, currentMessage);
    }

    private ClientLorebookLibraryStore lorebookLibraryStore() {
        if (lorebookLibrary == null) {
            Path path = configDirectory().resolve("lorebook-library");
            lorebookLibrary = new ClientLorebookLibraryStore(path);
        }
        return lorebookLibrary;
    }

    private ClientLorebookRequestContextLoader lorebookRequestContextLoader() {
        if (lorebookContextLoader == null) {
            lorebookContextLoader = new ClientLorebookRequestContextLoader(lorebookLibraryStore(), characterCards()::boundCard);
        }
        return lorebookContextLoader;
    }

    private CharacterCards characterCards() {
        if (characterCards == null) {
            characterCards = new CharacterCards(
                    new CharacterCardFolder(configDirectory().resolve("character-cards")),
                    new CardBindingStore(configDirectory().resolve("card-bindings.json")));
        }
        return characterCards;
    }

    private ClientLorebookInbox lorebookInbox() {
        Path path = configDirectory().resolve("lorebook-inbox");
        return new ClientLorebookInbox(path);
    }

    private void updateCharacterProfile(java.util.UUID npcId, NpcProfileScreen screen, NpcProfile profile) {
        long requestId = nextRequestId.getAndIncrement();
        var response = profileInbox.expect(npcId, requestId).orTimeout(3, TimeUnit.SECONDS);
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

    private void sendCookRequest(java.util.UUID npcId) {
        long requestId = nextRequestId.getAndIncrement();
        PacketDistributor.sendToServer(new SkillRequestPayload(npcId, requestId, SkillId.COOK.name()));
    }

    private boolean isMcaEntity(Entity entity) {
        return "mca".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    static void showLocal(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(message);
        }
    }

    private Optional<ConversationMemoryKey> currentMemoryKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || conversation.target().isEmpty()) {
            return Optional.empty();
        }
        Optional<String> worldIdentity = currentWorldIdentity(minecraft);
        return worldIdentity.map(identity -> new ConversationMemoryKey(
                identity,
                minecraft.player.getUUID(),
                conversation.target().orElseThrow().npcId()));
    }

    /**
     * Key that separates memories per world. Multiplayer uses the address as typed, so the same server
     * reached through a different address gets separate memories.
     */
    static Optional<String> currentWorldIdentity(Minecraft minecraft) {
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

    private ConversationMemoryStore memoryStore() {
        Path path = configDirectory().resolve("private-memories");
        return new ConversationMemoryStore(path);
    }

    private ProviderSettingsStore providerSettingsStore() {
        Path path = configDirectory().resolve("provider.json");
        return new ProviderSettingsStore(path);
    }

    /** config/plastic_memories/, where everything this client stores lives. */
    private static Path configDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(PlasticMemories.MOD_ID);
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
            case STORAGE_READ_ONLY -> "Profiles are read-only: this world was saved by a newer Plastic Memories version.";
        };
    }

    private record ProviderContext(CookAvailability availability, NpcProfileResponse profileResponse) {
    }
}
