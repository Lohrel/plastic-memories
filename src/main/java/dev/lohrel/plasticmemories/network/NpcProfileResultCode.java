package dev.lohrel.plasticmemories.network;

/** Sent over the network by ordinal: only append new values, never reorder or remove. */
public enum NpcProfileResultCode {
    SUCCESS,
    INVALID_REQUEST,
    INVALID_NPC,
    OUT_OF_RANGE,
    PERMISSION_DENIED,
    RATE_LIMITED,
    REPLAYED
}
