# ADR 004: Use Client-Side Chat Commands

- Status: Accepted

## Decision

Do not add a dedicated conversation screen. The player starts a private conversation with the client-side command `/plasticmemories "<npc name>"`. Responses and conversation status appear in Minecraft's normal chat interface.

While private conversation mode is active, ordinary player messages are intercepted and consumed locally before Minecraft can send a chat packet. A separate client-side command leaves private mode.

## Privacy requirement

The selection command, private messages, provider requests, and NPC replies must never be forwarded to the server. If interception or target resolution is uncertain, fail closed and do not transmit the text.

## Target resolution

Resolve only nearby, client-known, supported NPCs. Match names predictably. Missing and ambiguous names do not start a conversation. The server independently resolves and validates the NPC only when the client later requests a shared-world skill.

## Consequences

- No custom conversation GUI is required.
- The normal chat HUD must clearly distinguish private conversation from server chat.
- Client command registration and chat interception require dedicated tests because an interception regression could disclose a private message.
