# Privacy and Security Model

## Protected client-only data

The following data must never be sent to the Minecraft server:

- Provider API keys and authorization headers
- Private player messages
- Private NPC replies
- Full provider requests or responses
- Player-private conversation history
- Player-private NPC memories
- Prompt templates containing private context

Provider use is not local privacy: a remote provider receives the content sent directly by the player's client. A local provider is required when the player wants to avoid third-party disclosure.

## Server-visible data

The server may receive only data needed to coordinate shared gameplay:

- Sending player's authenticated connection identity
- Target NPC identifier
- Registered skill identifier
- Skill-specific bounded arguments
- Requests to publish a selected memory
- Requests to alter configuration

The server owns public NPC state, affinities, inventories, active tasks, and public memories.

Character-card Description, Personality, Appearance, and Backstory fields are public server-owned NPC state. They are sent only to requesting nearby clients, persisted in world save data, and included by each client in its own provider prompt. Only server operators or the singleplayer/LAN host may update them.

## Public snapshots

Public snapshots use an explicit allowlist. They may include name, profession, appearance descriptors, marriage/family status, public relationship facts, public memories, and available skills. They must not be produced by serializing entire MCA entities or arbitrary NBT.

## Untrusted clients

A player can modify their client, fabricate an LLM response, or send a skill packet manually. This is acceptable only because a skill packet has no more authority than the equivalent permitted GUI action.

For every request, the server validates:

- Packet and protocol version
- Payload size, string length, collection count, and numeric ranges
- Registered skill and permitted arguments
- Sender, NPC existence, dimension, distance, and loaded state
- Affinity and configuration lock
- NPC availability and cooldown
- Inventory and ingredient availability
- Supported container type, loaded state, vanilla interaction permission, lock state, and blocked-chest state
- World permissions and game rules

A failed check performs no partial world mutation.

## LLM output

LLM output is untrusted text. It cannot name classes, invoke commands, select arbitrary coordinates, construct registry identifiers outside an allowlist, or provide serialized game data. Unknown output becomes conversation-only behavior (`NONE`).

## Inventory integrity

Server tasks use normal server-owned inventory operations. Multi-step tasks reserve or revalidate resources to prevent duplication and races. Items are removed only when the corresponding operation succeeds, and interrupted tasks retain an auditable deterministic state without logging private text.

Container-search capability responses expose only `AVAILABLE`, `NO_FOOD`, or `BUSY`; container positions, contents, item stacks, and block-entity NBT never cross Minecraft networking or enter provider context. COOK may inspect any nearby unlocked vanilla chest, trapped chest, or barrel that passes vanilla world-interaction checks. Third-party claim/ownership systems are not integrated yet, so multiplayer servers must treat unprotected supported containers within range as shared with eligible NPC tasks.

## Memory visibility

Private memories are keyed locally by world identity, player identity, and NPC identity. Prompts for one player include only public NPC information and that player's private context.

Publishing is explicit and server-validated. Published data is treated as public permanently until an authorized deletion occurs. The UI must show the exact content before publishing.

## Credential handling

- Never put credentials in server configuration or synchronized NeoForge config.
- Offer session-only credentials and local persistence.
- Keep persisted credentials in a dedicated client-only location with restrictive permissions where the operating system supports them.
- Redact authorization headers and provider bodies from logs and exceptions.
- Never include real credentials in tests.

## Logging

Production logs may contain skill IDs, stable error codes, durations, and task state transitions. They must not contain private text, prompts, responses, credentials, authorization headers, or private memories.

## Abuse controls

- Per-player and per-NPC request cooldowns
- One active physical task per NPC unless explicitly designed otherwise
- Bounded provider response size and timeout
- Server-side deduplication/nonces for skill requests
- No world mutation from client-reported task completion
- Fail-closed behavior on version mismatch
