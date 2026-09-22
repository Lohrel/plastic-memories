# External lorebook and character-card interoperability

This document defines the client-local import contract from ADR 008. It is the acceptance baseline for source compatibility.

## Compatibility profiles

| Profile | Initial execution target |
| --- | --- |
| `SILLY_TAVERN` | World Info keyword activation, secondary-key logic, disabled/constant entries, scan depth, recursive scanning, ordering, probability (respecting `useProbability`), timed effects, and before-character / after-character / at-depth placement. Author's-note, example-message and outlet placements and regex keys are kept but skipped, one entry at a time. |
| `MARINARA` | Parse native versioned exports. Before / after / at-depth placement uses Marinara's own position numbers. Fields without an implemented equivalent are reported and those entries skipped. |

Profiles are selected automatically from detected format: native Marinara envelopes use `MARINARA`; all other supported card and World Info formats use `SILLY_TAVERN`.

## Input detection order

The detector reads bounded bytes and checks the shape in this order:

1. Native Marinara envelope: `type` plus `version`.
2. Character Card V3: `spec: "chara_card_v3"`.
3. Character Card V2: `spec: "chara_card_v2"`.
4. Character Card V1 / Pygmalion-compatible object.
5. Classic World Info object with `entries`.
6. Unsupported file, with a non-content-bearing diagnostic.

PNG/APNG input scans valid bounded PNG chunks and prefers a `ccv3` metadata payload over `chara`. `.charx` input requires a root `card.json` entry and is read through bounded `ZipInputStream` processing.

## Normalized lorebook model

A normalized lorebook contains source metadata, profile, book settings, and a stable ordered collection of entries. An entry is never expanded once per keyword.

| Source concept | Normalized field |
| --- | --- |
| `key` / `keys` | `primaryKeys` |
| `keysecondary` / `secondary_keys` | `secondaryKeys` |
| `constant` | `constant` |
| `disable` / `enabled` | `enabled` |
| `selective` + `selectiveLogic` | `secondaryRule` |
| `order` / `insertion_order` | `order` |
| `position`, `depth`, `role`, `outletName` | `insertion` |
| `scanDepth`, case/whole-word/regex flags | `matchOptions` |
| `probability`, sticky/cooldown/delay, group fields | `activationState` |
| recursion fields | `recursionOptions` |

Only `constant` is unconditional. A normal entry requires a primary-key match. A selective entry also evaluates its declared secondary-key rule.

## Placement

Positions are interpreted with the numbering of the app that produced the file (checked against both apps' source code):

| Placement | SillyTavern `position` | Marinara `position` | Where it goes |
| --- | --- | --- | --- |
| Before character | 0 | 0 | System prompt, before the NPC profile and card |
| After character | 1 | 1 | System prompt, after the card |
| At depth | 4 | 2 | Its own chat message with the entry's `role`, `depth` messages from the end (0 = after the newest message) |
| Unsupported | 2, 3, 5, 6, 7 | 7 and others | Skipped |

`depth` and `role` only matter at depth. Character Card V2/V3 book entries store these settings under `extensions` (and `position` as `"before_char"`/`"after_char"`); the importer reads them the way SillyTavern's `convertCharacterBook` does.

## Macros

`{{user}}` and `<USER>` become the player's persona name (or Minecraft name), and `{{char}}` and `<BOT>` the NPC's name, case-insensitively, in the whole system prompt and in at-depth lore. Other SillyTavern macros (`{{random}}`, `{{time}}`, ...) are not implemented and are sent as written.

## Activation scope

- A lorebook can be switched on for **all NPCs**, for **one NPC** (one world + player + NPC), or both. It's included once either way.
- Character cards are not lorebook-library items. They live in `config/plastic_memories/character-cards/` and are picked per NPC from the profile screen (ADR 009). A picked card's embedded book comes with it.

## Character-card model

The client-local card model preserves the V1/V2/V3 card fields needed for prompt construction and user display:

- name, description, personality, scenario, first message, alternate greetings, and example dialogue;
- creator metadata and tags;
- `system_prompt` and `post_history_instructions`;
- the embedded `character_book`;
- V3 creator notes, nickname, source metadata, and asset descriptors;
- source extensions as bounded raw data for diagnostics and future export.

The card is bound locally to one world/player/NPC tuple. Its embedded book is client-local and has precedence over ordinary global imported lore unless the selected source profile says otherwise. Public NPC profile updates remain a separate explicit action.

## Prompt precedence

Prompt construction uses named regions rather than one opaque lore string:

1. Plastic Memories base identity.
2. Before-character lore.
3. Public NPC profile, then card narrative fields and supported card-specific directives.
4. After-character lore.
5. Immutable Plastic Memories response protocol, skill constraints, and safety rules.

At-depth lore is inserted into the chat history instead. Within a placement, the highest-priority entry sits closest to the conversation.

Lore has a budget (32 entries, 16,384 characters). Like SillyTavern, entries are taken highest `order` first and the rest are left out for that message; the message is never refused because of it. Sticky and cooldown timers only start for entries that were actually sent.

Imported content is delimited as card/lore data. It may shape characterization but cannot override the final protocol.

## Import result contract

Every import produces a structured result with:

- detected format and automatically selected profile;
- source filename only, never source content in logs;
- accepted entry count;
- rejected-entry count and stable diagnostic codes;
- explicit warnings for unsupported runtime features;
- how many entries will be skipped (malformed, unsupported placement, or regex);
- whether activation is possible. It is only impossible when the file itself couldn't be read; problems with single entries skip those entries.

No silent truncation, key duplication, semantic downgrade, or conversion to constants is permitted.

## Fixture matrix

Repository fixtures contain only synthetic/sanitized content. The automated suite uses the checked-in Marinara fixture at `src/test/resources/lorebook/marinara-native-basic.marinara.json`; the remaining cases use synthetic inputs created by the tests. Manual acceptance fixtures belong in the client-local inbox and are automatically imported when the lorebook library is opened or refreshed. Do not copy private card or lorebook files into the repository.

| Fixture | Expected result |
| --- | --- |
| Classic World Info object | Normal keyed entry remains keyed; constant entry is unconditional; disabled entry remains inactive. |
| Classic World Info secondary rules | `AND_ANY`, `NOT_ALL`, `NOT_ANY`, and `AND_ALL` use SillyTavern profile semantics. |
| Embedded character-book array | SillyTavern defaults; one entry remains one entry with all aliases. |
| CCv2 JSON | Card fields and embedded book are preserved and locally bindable. |
| CCv3 JSON | V3 fields are retained; unknown future fields warn without rejecting a valid card. |
| PNG metadata | `ccv3` wins over `chara`; malformed/chunk-oversized data fails closed. |
| Marinara native lorebook | Envelope detection succeeds; unsupported native-only fields are reported before activation. |
| Invalid or oversized input | No persistence, activation, provider request, packet, or content-bearing log. |

The manual must-pass matrix may additionally use sanitized World Info JSON, CCv2 JSON, and PNG card samples supplied locally. Their contents are not copied into source control, and the automated build does not depend on a user's Downloads directory.

## Bounds and privacy

The implementation bounds raw input, decompressed input, normalized storage, entry count, prompt-character content, and scoped evaluator state. It validates a source before reading unbounded content and validates serialized output before replacing a persisted document.

All imported files, card bindings, evaluation state, and reports are client-only. A file becomes provider-visible only after a player explicitly activates or binds it for a private conversation.
