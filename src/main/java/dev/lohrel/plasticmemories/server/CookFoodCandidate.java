package dev.lohrel.plasticmemories.server;

public record CookFoodCandidate(int slot, int count, boolean food) {
    public CookFoodCandidate {
        if (slot < 0 || count < 0) {
            throw new IllegalArgumentException("Invalid inventory candidate");
        }
    }
}
