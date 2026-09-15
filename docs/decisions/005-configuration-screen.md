# ADR 005: Separate Conversation and Configuration UI

- Status: Accepted

## Decision

Private conversation uses Minecraft's normal chat interface after the player enters conversation mode with a client-side command. Personality and public-information editing uses a dedicated NPC configuration screen.

## Data boundary

The configuration screen contains only server-authoritative public fields and authorization state. It does not display, request, or transmit provider credentials, private dialogue, provider responses, or player-private memories.

## Authorization

The server decides whether the player may edit. Opening a client screen never grants authority. Submitted changes use an allowlisted, length-bounded patch tied to the authenticated sender and server-resolved NPC. Per ADR 006, the server rechecks operator or singleplayer/LAN-host status immediately before applying the complete patch.

## Consequences

- Conversation remains lightweight and private in normal chat.
- Structured public information can use appropriate controls and validation.
- Personality is shared NPC state because it affects the public characterization supplied to every player's model.
