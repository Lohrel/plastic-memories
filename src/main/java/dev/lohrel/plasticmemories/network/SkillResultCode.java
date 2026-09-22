package dev.lohrel.plasticmemories.network;

/** Sent over the network by ordinal: only append new values, never reorder or remove. */
public enum SkillResultCode {
    STARTED,
    SUCCESS,
    NO_FOOD,
    NO_PATH,
    NPC_BUSY,
    RATE_LIMITED,
    OUT_OF_RANGE,
    INVALID_NPC,
    UNSUPPORTED_NPC,
    UNSUPPORTED_SKILL,
    INVALID_REQUEST,
    REPLAYED,
    INVENTORY_FULL,
    PERMISSION_DENIED,
    INTERRUPTED,
    NO_CONTAINER,
    NPC_INVENTORY_FULL
}
