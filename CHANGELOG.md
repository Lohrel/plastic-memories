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

### Changed

- Lorebook-library UI now focuses on activation, deactivation, binding, removal, and refresh instead of manual per-file importing.
- Chub is no longer a separate runtime compatibility profile. Chub-hosted card and lorebook files are handled through supported SillyTavern-compatible shapes.
- Ordinary lorebook activation remains client-global; character-card bindings remain scoped to a local world/player/NPC tuple.
- Client conversation orchestration now owns provider, memory, profile, skill, and lorebook request flow in one client-side coordinator.
- Gradle now resolves the pinned MCA Reborn dependency into ignored build output with checksum verification instead of requiring a local `run/mods` JAR.

### Fixed

- Marinara prompt roles now accept both numeric values and string names such as `system`, `user`, and `assistant`.
- Imported lorebooks preserve keyed entries, secondary-key behavior, disabled/constant state, insertion metadata, timed activation data, and recursion controls instead of flattening them silently.
- Unsupported runtime features and unsafe semantic approximations now prevent activation with structured diagnostics.

### Privacy

- Imported files, normalized lorebooks, card bindings, activation state, provider credentials, and private memories remain client-local; activated content is included only in requests sent to the configured provider.
- Imported content is not placed in Minecraft packets, server saves, or server logs.
