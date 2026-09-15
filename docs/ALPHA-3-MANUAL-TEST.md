# Alpha 3 manual test

Use the isolated PrismLauncher instance **Plastic Memories Test**.

## Provider

1. Run `/plasticmemories provider`.
2. Enter an OpenAI-compatible API base URL or full chat-completions URL, model ID, and API key.
3. Save. The key field must remain masked.

NanoGPT accepts `https://nano-gpt.com/api/v1` as the base URL.

## Persistent private memory

1. Select a nearby MCA villager with `/plasticmemories "<displayed name>"`.
2. Tell the NPC a harmless unique fact, such as `My test color is amber.`
3. Wait for the NPC reply before sending another message.
4. Run `/plasticmemories memory status`; it should report one stored turn.
5. Leave and re-enter the world, select the same NPC, then ask for the test color without repeating it.
6. Confirm the NPC answers from the earlier private context.
7. Select a different NPC and confirm it does not know the test fact.
8. Reselect the first NPC, run `/plasticmemories memory clear`, then confirm `memory status` reports zero turns.

## Privacy checks

- Private messages must not appear as ordinary server chat.
- Sending another message while a reply is pending must be consumed locally and rejected with a wait message.
- Provider failures must not add a memory turn.
- Memory files remain client-only and are stored with owner-only permissions where supported.

Do not use real secrets as conversation test data.
