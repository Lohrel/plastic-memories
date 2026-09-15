# First Vertical Slice

## Goal

Prove the complete privacy and authority boundary using one MCA villager and one deterministic skill.

## Scenario

A player privately tells an MCA NPC that they are hungry. The player's client calls their configured provider. The model responds privately and selects either `NONE` or `COOK`. For `COOK`, the server runs deterministic code that obtains existing food from an allowed nearby container and delivers it to the requesting player.

Cooking recipes, furnace use, fuel management, native NPCs, autonomous behavior, and long-term memory are deferred.

## Acceptance criteria

### Packaging

- One JAR loads on a Minecraft 1.21.1 NeoForge 21.1.234 client and dedicated server.
- MCA 7.7.36-beta.3 is declared as the initial required dependency.
- Dedicated-server startup never loads client GUI or rendering classes.

### Private conversation

- The client-side command `/plasticmemories "<npc name>"` selects one nearby supported MCA villager and enters private conversation mode in Minecraft's normal chat interface.
- The selection command and subsequent private messages are consumed on the client and never sent as server commands or ordinary chat packets.
- The chat interface clearly indicates the active private NPC and provides a client-side command to leave private mode.
- Ambiguous or missing NPC names fail closed without selecting a target.
- The provider request originates from the client.
- No API key, player message, prompt, provider response, or private memory appears in captured Minecraft packets or server logs.
- Another connected player receives none of the private conversation.

### Configuration screen

- A dedicated screen displays the server-authoritative personality and public information of the selected NPC.
- Opening the screen does not expose private dialogue, provider credentials, or player-private memories.
- Editing is available only when the server identifies the player as an operator or the singleplayer/LAN host.
- Every submitted field is length-bounded, allowlisted, and revalidated by the server before persistence.
- Unauthorized or stale edits fail without partially changing the NPC.

### Skill selection

- The client supports `NONE` and `COOK` only.
- Valid model output selects the expected skill.
- Malformed, unknown, oversized, or ambiguous output selects `NONE`.
- The client sends only the NPC identifier, protocol data, `COOK`, and bounded arguments.

### Server authorization

- The server rejects nonexistent, unloaded, out-of-range, busy, unauthorized, and rate-limited NPC requests.
- Rejected requests make no inventory or world change.
- Replayed requests do not duplicate items or start duplicate tasks.

### Deterministic Cook prototype

- The task searches only already-loaded chunks within 32 horizontal and 8 vertical blocks and examines no more than ten targets per request.
- It considers vanilla chests, trapped chests, and barrels that are unlocked, unblocked where applicable, and allowed by vanilla world-interaction checks.
- It selects existing edible food; it does not craft, smelt, or spawn items.
- The NPC reaches the container and player through normal pathing.
- Up to four food items move container → NPC → player through insert-first/remove-second transfers, with live revalidation at both effects.
- Failure returns a stable code including `NO_FOOD`, `NO_CONTAINER`, `NO_PATH`, `NPC_BUSY`, `INVENTORY_FULL`, `NPC_INVENTORY_FULL`, `PERMISSION_DENIED`, or `INTERRUPTED`.

### Verification

- Unit tests cover parser and authorization boundaries.
- GameTests cover successful transfer, unavailable food, interruption, and duplicate-request prevention.
- A manual two-player LAN test confirms private conversation isolation.
- The built JAR is exercised in a copy of Industrial Adventures+, never first in the primary world.

## Completion definition

The vertical slice is complete only when automated checks pass, the JAR builds, both client and dedicated-server startup succeed, and the two-player privacy test has recorded evidence without recording private content.
