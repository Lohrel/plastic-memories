# External lorebook and character-card interoperability

This document defines the client-local import contract from ADR 008. It is the acceptance baseline for source compatibility.

## Compatibility profiles

| Profile | Initial execution target |
| --- | --- |
| `SILLY_TAVERN` | Current World Info keyword activation, secondary-key logic, disabled/constant entries, scan depth, recursive scanning, ordering, probability, default system-region insertion, and supported timed effects. Non-default role/depth/outlet insertion is retained but rejected before activation. |
| `MARINARA` | Parse native versioned exports now. Execute only fields with an implemented equivalent; report the rest before activation. |

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

1. Plastic Memories base identity and public NPC profile.
2. Card narrative fields and supported card-specific directives.
3. Lorebook regions according to source insertion configuration.
4. Immutable Plastic Memories response protocol, skill constraints, and safety rules.

Imported content is delimited as card/lore data. It may shape characterization but cannot override the final protocol.

## Import result contract

Every import produces a structured result with:

- detected format and automatically selected profile;
- source filename only, never source content in logs;
- accepted entry count;
- rejected-entry count and stable diagnostic codes;
- explicit warnings for unsupported runtime features;
- whether activation is possible.

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
