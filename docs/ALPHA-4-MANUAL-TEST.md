# Alpha 4 Manual Test

Use only the isolated **Plastic Memories Test** PrismLauncher instance and a disposable world.

## Prepare

1. Spawn or find one adult MCA villager and give it a unique name.
2. Place the villager near several vanilla containers: chest, trapped chest, and barrel.
3. Put at least six bread in one container. Leave another empty.
4. Keep the NPC inventory empty and stand 5–10 blocks away with at least four free player-inventory spaces.
5. Select the villager:
   ```text
   /plasticmemories "<exact NPC name>"
   ```

## Shared character profile

As the singleplayer/LAN host or a server operator, run:
```text
/plasticmemories character
```

Enter distinctive text in Description, Personality, Appearance, and Backstory, then save. Close and reopen the screen. Expected: all four fields retain their exact values.

Have a non-operator friend select the same NPC and open `/plasticmemories character`. Expected: the same four fields are visible, but Save is disabled. Their private provider conversation should reflect the shared profile. After leaving and reopening the world, the profile must still be present.

## Lorebook and card import

Use a synthetic or otherwise sanitized World Info, character-card, or native Marinara fixture. Place it in the client-local inbox:

```text
config/plastic_memories/lorebook-inbox/
```

Open:
```text
/plasticmemories lorebook
```

Expected:

- The file is imported automatically when the library opens or is refreshed; no manual per-file Import button is required.
- Native `marinara_lorebook` and `marinara_character` files report Marinara semantics automatically. Other supported card and World Info shapes use SillyTavern semantics.
- Imported artifacts begin inactive. A normal lorebook requires explicit Activate; a character card requires explicit Bind to the selected local NPC.
- Reopening or refreshing the library does not create a duplicate artifact for the same inbox filename.
- Activating a normal lorebook makes it available in this client's private prompts. This activation is intentionally client-global; character-card bindings remain NPC-specific.
- Remove revokes the local activation or binding and deletes the local artifact.

## Container-search COOK

With no edible item in the NPC inventory, run:
```text
/plasticmemories cook
```

Expected:

- Chat says `COOK started`.
- The NPC walks to the nearest supported container without teleporting.
- At a chest or trapped chest, the lid opens; the NPC faces and swings toward every examined container. The interaction lasts about three seconds.
- An empty container is skipped and the NPC continues to another candidate.
- Up to four bread move from the first usable edible stack into the NPC inventory only after examination completes.
- The NPC walks back to the requesting player.
- Chat says `COOK succeeded`.
- The same quantity, up to four, enters the player inventory and leaves the NPC inventory.
- Total food across container, NPC, and player inventories is conserved.

Repeat with food in a barrel. Expected: the NPC examines it for about three seconds with an arm swing and barrel sound, then performs the same transfer.

Repeat around a doorway or corner. One transient path-calculation failure must not immediately return `NO_PATH`; five consecutive failed path attempts skip that container or end the task when no reachable candidate remains.

## Existing NPC food has priority

Put six bread in both the NPC inventory and a nearby chest, then request COOK.

Expected: the NPC walks directly to the player, transfers four bread from its own inventory, and does not open or modify the chest.

## Provider-selected COOK

With the provider configured and food available either in the NPC or a supported nearby container, tell the selected NPC:
```text
I'm hungry. Please bring me something to eat.
```

Expected:

- The private reply displays as ordinary dialogue; `REPLY:` and `SKILL:` are hidden.
- `COOK started` appears.
- The same deterministic search, examination, pickup, movement, and delivery occurs.
- The private message and reply never appear in ordinary server chat.
- The server sends only the sanitized `AVAILABLE`, `NO_FOOD`, or `BUSY` capability; no container position, inventory content, private text, or provider credential enters Minecraft packets.

## Failure and revalidation checks

### No container

Remove all food from the NPC and remove every supported container within 32 horizontal and 8 vertical blocks.

Expected: `COOK failed: no accessible food container was found nearby.` No item is created.

### No food

Leave supported containers nearby but remove every edible stack from them and from the NPC.

Expected: the NPC examines no more than ten candidates, reports that no food is available, and creates no item. A private provider request should receive `NO_FOOD` and should not start COOK.

### Locked and blocked chest

Lock one chest with vanilla lock data, and separately block another chest from opening. Put food in both and leave one ordinary usable food container farther away.

Expected: the locked and blocked targets are not mutated; the NPC skips them and uses the farther valid container. If no valid container remains, the task fails without moving any item.

### Changed during examination

While the NPC is examining a food container, remove or replace the food before the three seconds finish.

Expected: the live inventory is re-read at commit time. Nothing is duplicated, and the NPC tries another candidate or reports no food.

### Interrupted before and after pickup

First interrupt COOK before the three-second examination finishes. Expected: the source container retains its food.

Then start again, wait until food enters the NPC inventory, and move more than 80 blocks away or leave the world before delivery. Expected: the task stops and the retrieved food remains in the NPC inventory.

### Full NPC inventory

Fill the NPC inventory with full non-food stacks, leave food in a supported container, and request COOK.

Expected: the task reports that the NPC cannot carry food and leaves the container unchanged.

### Full player inventory

Let the NPC retrieve food, then fill every player inventory slot with full incompatible stacks before delivery.

Expected: the task reports that the player inventory is full; the food remains in the NPC inventory and no item is duplicated.

### Busy and cooldown

Place food in range, stand about 15 blocks away, and run the command twice quickly.

Expected: the first request starts; the second is rejected as busy or rate-limited. Only the first task may mutate inventories.

### Out of range

Select the NPC while nearby, walk more than 32 blocks away, then run the command.

Expected: the server rejects the request as too far away. No inventory changes.

## Multiplayer warning and protocol

Every LAN/dedicated-server participant must use the exact same protocol-v4 JAR. An unlocked, unprotected vanilla chest, trapped chest, or barrel within range is eligible even if another player placed it; third-party claim/ownership integrations are not implemented yet.

Verify that private provider settings, credentials, dialogue, and memories remain local to each player while COOK state and inventory effects remain server-owned.

## Regression checks

- Ordinary private conversation and memory still work.
- Shared character profiles still persist and enforce read-only access for non-operators.
- `/plasticmemories leave` restores server chat.
- `/plasticmemories help` lists `/plasticmemories cook` and `/plasticmemories character`.
- The hotbar remains normal.

Report the exact chat status, NPC route, examined container type and position, and before/after food counts for any failed case.
