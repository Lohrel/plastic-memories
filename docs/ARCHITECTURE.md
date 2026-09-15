# System Architecture

## Deployment

Plastic Memories Project is distributed as one NeoForge JAR and installed by every participant. The JAR contains side-isolated client code, shared contracts, and server code. A LAN host runs both the client and integrated-server portions from the same JAR.

## Trust boundaries

```text
Player input
    |
    v
Client-only conversation system -----> Player-selected AI provider
    |                                      |
    | private reply + selected skill <-----+
    |
    | bounded SkillRequest only
    v
Minecraft server -----> deterministic task engine -----> shared world
    |
    +---- public NPC snapshot/status ----> requesting client
```

Private text does not cross the client/server boundary. Shared-world actions necessarily expose their registered skill ID and bounded arguments to the server.

## Modules

### Client

- Client-side `/plasticmemories "<npc name>"` conversation command
- Private conversation mode rendered in Minecraft's normal chat interface
- Local provider configuration and API client
- Player-private conversation and memory store
- Public NPC snapshot plus private-context prompt builder
- Sanitized per-skill availability lookup before provider requests
- Compact skill catalog and model-output parser
- Client-side response rendering
- NPC configuration screen for editing authorized personality and public information

### Shared

- Stable NPC identifiers
- Sanitized public NPC snapshot schema
- Registered skill IDs and bounded argument schemas
- Skill request/result packets
- Sanitized capability request/response packets
- Versioned protocol and persistence contracts
- Project-owned NPC interfaces

### Server

- Packet validation and rate limiting
- Operator/singleplayer-host authorization for shared profile edits
- Public NPC state and public memories
- World-persistent character profiles keyed by stable NPC UUID; editing is restricted to operators or the singleplayer/LAN host
- Deterministic task scheduling and execution
- Loaded-chunk container discovery, bounded visited-target state, timed interaction, pathing, inventory, and permission validation
- Structured skill results without private dialogue

### MCA adapter

The only module allowed to import MCA implementation classes. It maps an MCA villager to project-owned interfaces and converts MCA state into an allowlisted public snapshot. Prefer public MCA APIs; use minimal Mixins only where no suitable hook exists.

### Future native adapter

A later native NPC entity implements the same project-owned interfaces. Client conversation, memory, skill routing, protocol, and deterministic task contracts remain unchanged.

## Core interfaces

The exact Java API will be driven by failing tests, but responsibilities are fixed:

- `NpcHandle`: stable identity and access to allowlisted public state
- `NpcAuthorization`: whether a player may converse, configure, or assign a skill
- `NpcInventory`: validated inventory operations
- `NpcSkill`: registered high-level intention and bounded argument schema
- `NpcTask`: deterministic server-side execution state machine
- `SkillResult`: stable status code and non-private result data

## Skill protocol

The model selects a registered skill, not a low-level operation. The client parser accepts a strict, small response format. Unknown, ambiguous, oversized, or malformed output resolves to `NONE`.

The server binds a request to the sending player and resolves the NPC by server-known identity. Clients cannot supply authority, affinity, inventory contents, arbitrary positions, or completion results.

`COOK` is the first multi-stage physical task: use carried food if available, otherwise search already-loaded chunks for up to ten nearby supported containers, travel, examine for 60 ticks, transfer up to four food items into the NPC, travel to the requester, and transfer them again. Candidate positions are derived entirely on the server and never enter the provider prompt or packet payload. Mutable block, inventory, reach, permission, and destination-capacity state is reacquired immediately before each effect.

## Threading

- Provider requests run asynchronously on the client and never block the render thread.
- Packet decoding is bounded before scheduling work.
- World and inventory mutations run on the server thread.
- Task planning may calculate asynchronously only from immutable snapshots; every mutation and precondition is revalidated on the server thread.

## Compatibility policy

Initial compatibility is exactly Minecraft 1.21.1, NeoForge 21.1.234, and MCA 7.7.36-beta.3. Additional versions and loaders are separate ports after the vertical slice is stable.
