# ADR 003: LLMs Select Server-Authoritative Skills

- Status: Accepted

## Decision

A language model may choose a registered high-level skill such as `COOK`. It cannot issue low-level commands, coordinates, inventory mutations, or direct world operations. The server executes deterministic skill code after full authorization and state validation.

## Rationale

This supports small models, keeps behavior predictable, preserves normal survival constraints, and makes a manually fabricated client request no more powerful than an authorized GUI action.

## Failure behavior

Unknown or malformed model output becomes `NONE`. Invalid or unauthorized packets cause no world mutation. Tasks return stable result codes that the client may privately present or ask the model to verbalize.
