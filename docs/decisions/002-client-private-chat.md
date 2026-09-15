# ADR 002: Keep Private Conversation Client-Side

- Status: Accepted

## Decision

Provider credentials, private dialogue, provider responses, and player-private memories remain on the player's client. The client contacts the selected provider directly.

## Consequences

- The Minecraft server cannot log or inspect private conversation through this mod.
- Every player supplies and controls their own provider credentials.
- Different players may receive different private portrayals of the same NPC.
- Shared facts come from an allowlisted server-authoritative public snapshot.
- A remote model provider still receives the content sent by that player.
- Physical skills reveal only their registered invocation and bounded arguments to the server.
