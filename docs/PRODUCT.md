# Product Definition

## Intent

Plastic Memories Project gives Minecraft NPCs private, persistent relationships with individual players while allowing language models to select safe high-level intentions. Deterministic game code—not the language model—performs physical actions.

## Player experience

A player can select an NPC with the client-side command `/plasticmemories "<npc name>"` and then speak naturally through Minecraft's normal chat interface. While private conversation mode is active, ordinary message text is consumed locally rather than sent as server chat. The NPC answers through the player's chosen model and may select an available skill such as cooking, following, fetching, or guarding. Skills obey normal inventories, travel, time, permissions, and world state.

## Privacy

Each player's provider credentials, private conversations, provider responses, and private memories remain on that player's client. Another player talking to the same NPC uses their own provider and receives only server-authoritative public NPC information plus their own local private context.

## Shared NPC state

The server owns physical and multiplayer-relevant facts, including NPC identity, inventory, family or marriage status, affinity, configuration lock, tasks, and explicitly published memories.

## Configuration ownership

NPC character profiles are shared server state. Every nearby player may view them, while only server operators or the singleplayer/LAN host may edit them. The dedicated configuration screen exposes Description, Personality, Appearance, and Backstory. Private conversation continues to use Minecraft's normal chat interface.

## Initial dependency

The first implementation uses MCA Reborn villagers to validate conversation, privacy, memory, and skill systems. MCA remains behind an adapter so a later native NPC implementation can replace it without rewriting the core systems.

## Out of initial scope

- A native NPC entity implementation
- Multiple Minecraft versions or mod loaders
- Autonomous life simulation
- Replacing MCA relationships or families
- Arbitrary LLM-generated commands or coordinates
- Paid or account-entitlement-gated gameplay features
