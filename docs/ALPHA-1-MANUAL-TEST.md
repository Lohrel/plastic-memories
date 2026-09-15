# Alpha 1 manual test

Use the PrismLauncher instance **Plastic Memories Test**. It is an isolated minimal instance containing only MCA Reborn and `plastic_memories-0.1.0-alpha.1.jar`; do not use the primary modpack world for this test.

## Test

1. Launch the test instance and create a new Creative test world.
2. Spawn or find an MCA villager, stand within 32 blocks, and note its displayed name.
3. Run `/plasticmemories "<displayed name>"`, keeping the quotes for names with spaces.
4. Confirm the chat says `Private conversation started with <displayed name>`.
5. Send `This must stay private` as a normal chat message.
6. Confirm you see `[You -> <displayed name>] This must stay private` and the local Alpha 1 test reply.
7. In multiplayer only, confirm no other connected player sees that message in server chat.
8. Run `/plasticmemories leave`.
9. Send a harmless normal message and confirm it reaches server chat again.

## Failure cases

- A missing name must report that no nearby MCA NPC was found.
- Two nearby MCA NPCs with the same name must be rejected as ambiguous.
- Leaving the world must automatically leave private mode.

Do not enter secrets. Alpha 1 has no AI provider; its reply is intentionally local and deterministic.
