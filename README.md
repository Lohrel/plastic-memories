# Plastic Memories Project

A Minecraft NPC interaction mod focused on private client-side AI conversations and deterministic, server-authoritative NPC skills.

## Initial target

- Minecraft 1.21.1
- NeoForge 21.1.234
- Java 21
- MCA Reborn 7.7.36-beta.3 as the first NPC implementation
- One JAR containing client, shared, and server code

## Current build

`0.1.0-alpha.4` adds the first server-authoritative gameplay skill to the private-conversation build:

- The provider returns strict private dialogue plus `NONE` or `COOK`; malformed output cannot invoke a skill.
- Before each provider request, the server returns only a sanitized `AVAILABLE`, `NO_FOOD`, or `BUSY` COOK capability; the client includes it in the private prompt so the NPC does not promise unavailable food.
- `COOK` packets contain only the NPC UUID, request ID, and registered skill ID—never private text.
- The server independently validates the sender, exact MCA NPC identity, adult/alive state, distance, nonnegative MCA affinity, spectator permission, MCA job availability, cooldown, monotonic request ID, and Plastic Memories busy state.
- `COOK` first uses edible items already in the MCA NPC's inventory. Otherwise, the NPC searches already-loaded chunks within 32 horizontal and 8 vertical blocks for the nearest vanilla chest, trapped chest, or barrel.
- The NPC examines up to 10 containers for 60 ticks each, skips blocked, locked, missing, or unreachable targets, carries up to four food items in its own inventory, then walks back and gives them to the requesting player.
- Both pickup and delivery use insert-first/remove-second inventory mutation. If the task stops after pickup, the retrieved food remains in the NPC inventory rather than being lost.
- Missing food or containers, path failures, interruption, NPC/player inventory limits, duplicate requests, and invalid requests return stable status codes.
- `/plasticmemories cook` directly exercises the same server-authoritative path for deterministic testing.
- `/plasticmemories "John"` selects one nearby MCA NPC (within 32 blocks) by exact display name.
- Normal chat is cancelled locally while private mode is active and shown only in the local chat HUD.
- `/plasticmemories provider` configures a generic OpenAI-compatible provider in a client-only screen.
- `/plasticmemories character` opens the selected NPC's shared character-card profile with Description, Personality, Appearance, and Backstory tabs.
- Character profiles are persisted in server world data, visible to nearby players, and editable only by server operators or the singleplayer/LAN host.
- All four public profile fields are included in that NPC's private provider system prompt for every player.
- The provider is called asynchronously from the client; private text and credentials never enter Minecraft packets.
- The newest 12 successful conversation turns persist locally per world, player, and NPC.
- `/plasticmemories memory status` and `/plasticmemories memory clear` inspect or clear the selected NPC's private memory.
- `/plasticmemories status`, `/plasticmemories help`, and `/plasticmemories leave` provide conversation controls.

COOK may use any supported nearby vanilla container that is unlocked, unblocked, loaded, and permitted by vanilla world interaction checks. On multiplayer servers this can include another player's unprotected chest; third-party claims/ownership integrations, recipe cooking, fuel, and modded-container support remain later milestones. Container-search result codes require multiplayer protocol v4, so every participant must use the exact same JAR.

See [Alpha 4 manual test](docs/ALPHA-4-MANUAL-TEST.md).

## Canonical documents

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy and security](docs/PRIVACY-AND-SECURITY.md)
- [First vertical slice](docs/MVP.md)
- [Architecture decisions](docs/decisions/)
