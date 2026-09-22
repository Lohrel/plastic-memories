# Changelog

All notable changes to Plastic Memories are tracked here. Changes under `[Unreleased]` are not yet published as a release.

## [Unreleased]

### Added

- Client-local lorebook and character-card library with inactive-by-default imports.
- Automatic inbox scanning when the lorebook library opens or refreshes.
- Duplicate-safe storage for files already imported from the inbox.
- Support for SillyTavern-compatible World Info, Character Card V1/V2/V3 JSON, PNG/APNG card metadata, `.charx` packages, and native Marinara envelopes.
- Automatic compatibility selection: native Marinara envelopes use Marinara semantics; other supported sources use SillyTavern semantics.
- Private prompt integration for activated lorebooks and locally bound character cards.
- Persistence migration for libraries created with the removed legacy profile names.

- Character-cards folder (`config/plastic_memories/character-cards/`) with a private portrait picker. PNG portraits and `.charx` icons are shown.
- Personas: saved player personas (name + description) with an active one, per-NPC locks, a manager screen (`/plasticmemories persona`) and `/plasticmemories persona next`.
- `{{user}}`/`<USER>` and `{{char}}`/`<BOT>` macros in cards, lorebooks and profiles are replaced with the persona and NPC names.
- "My card" view on the NPC profile screen: see and edit your card's fields for that NPC without changing the file.
- Lorebooks can be switched on for a single NPC, in addition to all NPCs.
- At-depth lore placement, inserted into the chat history like SillyTavern and Marinara.
- Optional sampling settings (temperature, top P/K, min P, penalties, max tokens), sent only when set.
- Compatibility fixtures for every saved format, and migration support for NPC profile data.

### Changed

- The lorebook library holds lorebooks only; character cards moved to their own folder.

- Lorebook-library UI now focuses on activation, deactivation, binding, removal, and refresh instead of manual per-file importing.
- Chub is no longer a separate runtime compatibility profile. Chub-hosted card and lorebook files are handled through supported SillyTavern-compatible shapes.
- Ordinary lorebook activation remains client-global; character-card bindings remain scoped to a local world/player/NPC tuple.
- Client conversation orchestration now owns provider, memory, profile, skill, and lorebook request flow in one client-side coordinator.
- Gradle now resolves the pinned MCA Reborn dependency into ignored build output with checksum verification instead of requiring a local `run/mods` JAR.

- The mod now defaults to the provider's own temperature instead of forcing 0.
- Lorebook entries with unsupported placements or regex keys are skipped one by one instead of blocking the whole lorebook.
- Lore over the prompt budget is trimmed by priority instead of failing the message.

### Fixed

- Character cards with `spec_version` (every real V2/V3 card) and a character book were rejected.
- Character-book entry settings stored under `extensions` (position, depth, role, probability, groups, timing) were ignored.
- SillyTavern positions were read with Marinara's numbering, and `depth` blocked before-character entries.
- `useProbability: false` was ignored.
- Unreadable, corrupt, or newer-version save files could be overwritten with empty data. NPC profile data from a newer version is now kept read-only.
- NPC profile data no longer passes through vanilla's level data fixer.

- Marinara prompt roles now accept both numeric values and string names such as `system`, `user`, and `assistant`.
- Imported lorebooks preserve keyed entries, secondary-key behavior, disabled/constant state, insertion metadata, timed activation data, and recursion controls instead of flattening them silently.
- Unsupported runtime features and unsafe semantic approximations now prevent activation with structured diagnostics.

### Privacy

- Imported files, normalized lorebooks, card bindings, activation state, provider credentials, and private memories remain client-local; activated content is included only in requests sent to the configured provider.
- Imported content is not placed in Minecraft packets, server saves, or server logs.
