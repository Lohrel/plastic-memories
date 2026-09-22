# Agent Instructions

## Read first

Before changing code, read `README.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/PRIVACY-AND-SECURITY.md`, `docs/MVP.md`, and every relevant record under `docs/decisions/`.

## Non-negotiable boundaries

- Keep private dialogue, provider credentials, provider responses, and player-private memories entirely client-side.
- Never serialize private content into a Minecraft packet, server config, server save, server log, telemetry event, crash message, or exception text.
- Treat LLM output and every client packet as hostile input.
- Let the LLM select only registered high-level skills with bounded arguments. Never let it issue coordinates, commands, inventory mutations, or world operations.
- Validate skill authorization, NPC identity, distance, ownership, affinity, arguments, cooldowns, inventory, container access, and world permissions on the server.
- Perform all shared-world mutations through deterministic server code on the server thread.
- Keep MCA behind project-owned interfaces. Do not copy MCA source code or assets.
- Preserve a single distributable JAR while isolating client-only classes from dedicated-server class loading.

## Development discipline

- Follow strict RED-GREEN-REFACTOR for behavior changes: add one failing test, run it and confirm the expected failure, implement the minimum behavior, then run the focused and full suites.
- Prefer vertical slices over framework-building and speculative abstraction.
- Do not add dependencies when Java, NeoForge, or an existing dependency provides the required behavior.
- Use stable identifiers and version persisted/networked data from its first release.
- Bound packet sizes, strings, collections, quantities, ranges, timeouts, retries, and provider responses.
- Fail closed: malformed model output becomes `NONE`; invalid packets make no world change.
- Never weaken privacy or server validation to simplify a test.

## Comments

Write comments for a human maintainer reading the code for the first time.

- Give every non-trivial class a one-line Javadoc saying what it is for. Skip it for self-explanatory records and enums.
- Add inline comments only where the reason is not obvious from the code: magic numbers, fallbacks, ordering requirements, game-engine quirks, deliberate asymmetries.
- Explain *why*, not *what*. Do not narrate the next line.
- Use plain words. Avoid stacked jargon such as "content-safe", "provider-visible", "client-local" in every sentence.
- Do not restate the boundaries in this file inside code comments. They apply everywhere; mention one in a comment only where the code would look wrong or pointless without it.
- Keep comments to one or two lines. If a method needs a paragraph, it probably needs to be split or renamed.
- Update or delete a comment when the code it describes changes.

Good: `// Re-path every 10 ticks (0.5 s); computing a new path every tick is wasteful.`
Bad: `/** Stores an inactive import report. Storing does not make content provider-visible. */`

## Verification before completion

- Run the repository check command.
- Run focused tests for the changed behavior.
- Build the distributable JAR.
- For game-facing changes, launch the appropriate Minecraft environment and exercise the representative behavior.
- Review the diff for secrets, private-content logging, unsafe packet handling, unrestricted world mutation, and accidental MCA coupling.
- Obtain an independent code review before committing any multi-file behavior change.
- Report only verification actually executed.
