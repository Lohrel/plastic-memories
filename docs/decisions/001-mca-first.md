# ADR 001: Use MCA Villagers First

- Status: Accepted

## Decision

Use MCA Reborn 7.7.36-beta.3 villagers as the initial NPC implementation for Minecraft 1.21.1 NeoForge. Access MCA only through a project-owned adapter boundary.

## Rationale

The project can validate its distinctive private conversation, memory, skill-selection, and deterministic task systems without first implementing human NPC rendering, identity, relationships, families, and persistence.

## Constraints

- Do not fork MCA for the prototype.
- Do not copy MCA source code or assets.
- Keep direct MCA imports inside the MCA adapter.
- Prefer public APIs and use minimal Mixins only when necessary.
- Record every Mixin target because MCA updates can break it.

## Exit path

Implement a native NPC adapter against the same project interfaces, migrate public state deliberately, and make MCA integration optional once native NPC behavior is viable.
