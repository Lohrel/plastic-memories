# ADR 006: Shared Operator-Managed Character Profiles

- Status: Accepted

## Decision

Each NPC has one shared server-side character profile keyed by its stable NPC UUID. The allowlisted fields are Description, Personality, Appearance, and Backstory. All four fields are supplied to each player's client-side provider prompt.

Every nearby player may view the profile. Only a server operator or the singleplayer/LAN host may update it. The server re-resolves the NPC, checks range and edit authority, validates every field bound, and applies the complete profile atomically.

## Rationale

A shared profile gives every player the same public characterization while private dialogue and memories remain player-local. Operator/host editing avoids first-player ownership races and unauthorized multiplayer changes.

## Data boundary

Character profiles are public Minecraft server data. Provider credentials, private prompts, dialogue, responses, and private memories are never included in profile packets or world persistence.
