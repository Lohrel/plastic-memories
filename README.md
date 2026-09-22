# Plastic Memories Project

A Minecraft NPC interaction mod focused on private client-side AI conversations and deterministic, server-authoritative NPC skills.

## Initial target

- Minecraft 1.21.1
- NeoForge 21.1.234
- Java 21
- MCA Reborn 7.7.36-beta.3 as the first NPC implementation
- One JAR containing client, shared, and server code

## Build and test

The repository targets Java 21. The first Gradle build downloads the pinned MCA Reborn NeoForge dependency into the ignored `build/dependencies/` directory and verifies its SHA-256 checksum; the local Minecraft `run/` directory is not a build prerequisite.

```bash
./gradlew test
./gradlew build
```

`build/libs/plastic_memories-<version>.jar` is the distributable mod JAR. Runtime behavior still requires testing in the disposable **Plastic Memories Test** PrismLauncher instance described in [the Alpha 4 manual test](docs/ALPHA-4-MANUAL-TEST.md).

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
- `/plasticmemories lorebook` opens the client-only lorebook/card library. It scans the inbox on open and refresh, automatically imports accepted files, and leaves imported data inactive until explicitly activated or locally bound; active content reaches only the player's provider prompt.
- `/plasticmemories character` opens the selected NPC's shared character-card profile with Description, Personality, Appearance, and Backstory tabs.
- Character profiles are persisted in server world data, visible to nearby players, and editable only by server operators or the singleplayer/LAN host.
- All four public profile fields are included in that NPC's private provider system prompt for every player.
- The provider is called asynchronously from the client; private text and credentials never enter Minecraft packets.
- The newest 12 successful conversation turns persist locally per world, player, and NPC.
- `/plasticmemories memory status` and `/plasticmemories memory clear` inspect or clear the selected NPC's private memory.
- `/plasticmemories status`, `/plasticmemories help`, and `/plasticmemories leave` provide conversation controls.

## Character cards

Put character cards (PNG, JSON or `.charx`) in:

```text
config/plastic_memories/character-cards/
```

Open an NPC's profile (`/plasticmemories character`), switch to **My card**, and press **Card...** to pick one from a portrait grid. My card shows the card's fields (description, personality, scenario, first message, examples, system prompt, post-history) and lets you edit them for that NPC; **Reset to file** undoes your edits. Everything here is private: it's stored on your computer only, per world and NPC, and the card file itself is never changed. The folder is the collection, so editing or deleting a file there changes what the picker shows.

## Personas

`/plasticmemories persona` opens your saved personas: who you are to the NPCs, like SillyTavern and Marinara personas. Each has a name and a description.

- **Use** makes a persona active for every NPC. `/plasticmemories persona next` switches to the next saved persona without opening the screen.
- **Lock to NPC** makes a persona the one a specific NPC always sees, whatever is active.
- The name replaces `{{user}}` (and `<USER>`) in cards, lorebooks and profiles; `{{char}}` (and `<BOT>`) becomes the NPC's name. With no persona, your Minecraft name is used.

Personas are stored only on your computer, in `config/plastic_memories/personas.json`.

## Lorebook imports

Drop lorebook files into:

```text
config/plastic_memories/lorebook-inbox/
```

The library scans this directory when opened or refreshed. Accepted files are imported into client-local storage without a network upload and stay off until the player switches them on for all NPCs or for one NPC.

Supported source shapes include:

- SillyTavern-compatible World Info and embedded character books;
- Character Card V1/V2/V3 JSON;
- V2/V3 card metadata in PNG/APNG files;
- V3 `.charx` card packages;
- native Marinara `marinara_lorebook` and `marinara_character` envelopes.

Profile selection is automatic: native Marinara envelopes use Marinara semantics, while all other supported sources use SillyTavern semantics. Chub is not a separate runtime profile; Chub-hosted card and lorebook files are handled through the supported SillyTavern-compatible shapes. Existing local libraries using the removed profile names are migrated to SillyTavern when loaded.

Imported content remains client-only and is included in private provider prompts only after it is switched on or picked.

COOK may use any supported nearby vanilla container that is unlocked, unblocked, loaded, and permitted by vanilla world interaction checks. On multiplayer servers this can include another player's unprotected chest; third-party claims/ownership integrations, recipe cooking, fuel, and modded-container support remain later milestones. Result codes are part of the multiplayer protocol (currently v5), so every participant must use the exact same JAR.

See [Alpha 4 manual test](docs/ALPHA-4-MANUAL-TEST.md).

## Canonical documents

- [Changelog](CHANGELOG.md)
- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Interoperability](docs/INTEROPERABILITY.md)
- [Privacy and security](docs/PRIVACY-AND-SECURITY.md)
- [First vertical slice](docs/MVP.md)
- [Architecture decisions](docs/decisions/)
