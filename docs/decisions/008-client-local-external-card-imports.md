# ADR 008: Client-local external lorebook and character-card imports

- Status: Accepted

## Decision

Import external lorebooks and character cards only into client-local persistence. The compatibility targets are SillyTavern World Info / Character Card formats and native Marinara exports.

The client recognizes formats by bounded file content and declared schema markers, not filename alone:

- classic SillyTavern World Info JSON;
- Character Card V1, V2, and V3 JSON;
- Character Card V2/V3 metadata embedded in PNG/APNG;
- Character Card V3 `.charx` packages;
- native Marinara `marinara_lorebook` and `marinara_character` versioned envelopes.

An imported character card creates a player-local binding to a selected NPC. Its embedded character book is client-local and participates in that binding's prompt context. Importing a card does not mutate server-owned NPC state. Copying the allowlisted Description, Personality, Appearance, and Backstory fields to the shared NPC profile remains an explicit, authorized action under ADR 006.

Card `system_prompt` and `post_history_instructions` are preserved locally and may be used as card-specific provider context. Plastic Memories appends its immutable response protocol, skill constraints, and safety rules after all imported card content.

## Compatibility contract

A format is not considered compatible merely because JSON parsed. Every activated feature must either:

1. execute according to its declared compatibility profile; or
2. be rejected before activation with a structured, content-safe warning.

Importers must not silently convert primary keys into constants, duplicate an entry per alias, ignore disabled entries, discard secondary-key logic, or silently truncate lore text. The importer returns an explicit report containing accepted entries, rejected entries, and unsupported-feature warnings without logging imported private content.

Source behavior that depends on frontend-global settings is not exposed as a separate profile. Native Marinara envelopes select the Marinara profile automatically; all other supported formats use SillyTavern defaults.

## Privacy and authority boundary

External source files, normalized imports, character-card bindings, prompt templates, embedded lorebooks, import reports, and runtime state remain client-only. They never enter Minecraft packets, server save data, server configuration, telemetry, crash reports, or logs.

A player-selected remote provider receives active card/lore content in the same way it receives private conversation context. Dropping or scanning a file does not activate it or send it to a provider.

## Consequences

- The lorebook domain and evaluator must remain free of Minecraft, NeoForge, MCA, and provider transport imports.
- Persistence must be versioned, bounded by raw and serialized byte limits, and preserve source provenance and declared compatibility profile.
- Prompt construction requires named insertion regions rather than one undifferentiated lore block to support source insertion semantics safely.
- Java/JavaScript semantic differences, including unsupported regex or macro behavior, require explicit profile-specific support or visible rejection; they must never be silently approximated.
- Import UX belongs to a client-only screen or local file inbox, not a client command path that could fall through to the Minecraft server.
