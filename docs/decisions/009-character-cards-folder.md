# ADR 009: Character cards live in their own folder

- Status: Accepted
- Date: 2026-09-22
- Supersedes: the character-card half of ADR 008 (lorebook imports are unchanged)

## Context

ADR 008 imported character cards through the lorebook inbox into the lorebook library. That mixed two different things: a lorebook is world knowledge that can be switched on for many NPCs, while a card is who one NPC *is*. It also threw away the card's portrait, so players had no way to recognise cards except by file name.

## Decision

- `config/plastic_memories/character-cards/` is the card collection, like SillyTavern's characters folder. Whatever PNG, JSON or `.charx` card files are in it are the cards a player can pick. Nothing is copied; editing or deleting a file is reflected the next time the picker or a chat reads it.
- A player picks a card for an NPC from a portrait grid opened by the **Card...** button on the NPC profile screen. The choice is stored in `card-bindings.json` per world, player and NPC, by file name. A binding to a file that no longer exists means "no card".
- Portraits come from the PNG itself or from the main `icon` asset of a `.charx`. JSON cards show the character's initial.
- The bound card's embedded character book applies to that NPC, before the NPC's and the global lorebooks, as in SillyTavern.
- The NPC profile screen has two views: **Shared profile** (the server's, operator-editable, seen by everyone) and **My card** (this player's card, field by field). Edits in My card are stored per NPC in `card-bindings.json` as changed fields only, so unchanged fields keep following the file; the file itself is never written. **Reset to file** drops the edits, and picking a different card drops them too. Both the shared profile and the card go into the player's prompt.
- The lorebook library holds lorebooks only. Card files dropped in the lorebook inbox are counted and the player is told to use the cards folder. Card entries left in early v1 `library.json` files are ignored (the mod is in alpha and the maintainer chose not to migrate them).

## Privacy

Unchanged from ADR 008: card files, bindings and portraits stay on the player's machine. A card reaches the player's own provider only after the player picks it for an NPC. The shared server profile (ADR 006) remains a separate, explicit, operator-controlled thing.
