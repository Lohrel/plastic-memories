# 007: COOK nearby-container search

- Status: accepted

## Context

COOK previously delivered exactly one edible item already held in the NPC inventory. That made the skill safe but too limited. Minecraft Java 1.21.9's Copper Golem provides a useful interaction pattern: search loaded chunk block entities, travel to a bounded set of container targets, visibly examine a target for 60 ticks, reacquire live state, then mutate the inventory.

Plastic Memories targets Minecraft 1.21.1, so the behavior must be implemented in project-owned server code rather than depending on Copper Golem classes.

## Decision

For the first expanded COOK vertical slice:

- Prefer edible food already in the NPC inventory.
- Otherwise search only already-loaded chunks within 32 horizontal and 8 vertical blocks of the NPC.
- Support vanilla chests, trapped chests, and barrels.
- Consider any supported container that is unlocked, unblocked where applicable, and allowed by vanilla world-interaction checks.
- Sort candidates nearest-first and examine no more than 10 per request.
- Travel using the existing five-consecutive-failure rule, an 80-block requester/NPC separation bound that accommodates opposite corners of the search volume, and a total task deadline.
- Wait 60 ticks at each reachable container before reacquiring its live inventory.
- Take up to four items from the first edible stack that can be inserted into the NPC inventory.
- Insert into the NPC before removing from the container; later insert into the player before removing from the NPC.
- Keep retrieved food in the NPC inventory if delivery is interrupted or the player cannot accept it.
- Revalidate the requester, NPC authorization, busy state, reach, live block/container, lock, food, and destination capacity on the server.
- Keep provider-facing capability data restricted to `AVAILABLE`, `NO_FOOD`, or `BUSY`.
- Use protocol v4 because the result-code contract now includes container and NPC-inventory failures.

Chest interaction uses a server-driven lid event and sound without creating a player menu. Barrels use an interaction sound and NPC arm swing; 1.21.1's opener counter cannot safely represent a non-player opener.

## Consequences

- COOK can source food from the environment without exposing container contents or coordinates to the provider or client packet.
- Pickup and delivery conserve items across failure and interruption.
- Empty, invalid, locked, blocked, and unreachable candidates can be skipped in favor of another target.
- On multiplayer servers, any unprotected supported container in range may be used. Third-party claim and ownership integrations are required before promising claim-aware behavior.
- Modded item-handler containers, recipes, fuel, and persistent cross-request chest memory remain separate vertical slices.

## Reference

- [Minecraft 1.21.9 `TransportItemsBetweenContainers` mappings](https://mappings.dev/1.21.9/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.html)
- [Minecraft 1.21.9 `CopperGolemAi` mappings](https://mappings.dev/1.21.9/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.html)
