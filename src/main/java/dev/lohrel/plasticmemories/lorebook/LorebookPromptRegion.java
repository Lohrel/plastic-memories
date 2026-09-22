package dev.lohrel.plasticmemories.lorebook;

import java.util.List;
import java.util.Objects;

/** A stable grouping boundary that prevents lore insertion settings becoming one opaque text block. */
public record LorebookPromptRegion(
        LorebookPromptRegionName name,
        LorebookPromptRole requestedRole,
        int depth,
        String outletName,
        List<ImportedLorebookEntry> entries) {
    public LorebookPromptRegion {
        name = Objects.requireNonNull(name, "name");
        requestedRole = Objects.requireNonNull(requestedRole, "requestedRole");
        outletName = Objects.requireNonNull(outletName, "outletName");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
