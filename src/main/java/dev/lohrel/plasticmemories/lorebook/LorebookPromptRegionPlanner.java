package dev.lohrel.plasticmemories.lorebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts preserved source insertion data into deterministic application-owned prompt regions. */
public final class LorebookPromptRegionPlanner {
    private LorebookPromptRegionPlanner() {
    }

    public static List<LorebookPromptRegion> plan(List<ImportedLorebookEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<RegionKey, List<ImportedLorebookEntry>> grouped = new LinkedHashMap<>();
        for (ImportedLorebookEntry entry : entries) {
            LorebookInsertion insertion = entry.insertion();
            if (!supports(insertion)) {
                throw new IllegalArgumentException("Imported lore uses an unsupported prompt insertion mode.");
            }
            RegionKey key = new RegionKey(
                    nameFor(insertion.sourcePosition()),
                    insertion.role(),
                    insertion.depth(),
                    insertion.outletName());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        ArrayList<LorebookPromptRegion> planned = new ArrayList<>();
        for (Map.Entry<RegionKey, List<ImportedLorebookEntry>> group : grouped.entrySet()) {
            RegionKey key = group.getKey();
            planned.add(new LorebookPromptRegion(
                    key.name(), key.role(), key.depth(), key.outletName(), group.getValue()));
        }
        planned.sort(Comparator.<LorebookPromptRegion>comparingInt(region -> order(region.name()))
                .thenComparingInt(LorebookPromptRegion::depth)
                .thenComparing(region -> region.outletName()));
        return List.copyOf(planned);
    }

    /**
     * The current provider adapter has one safe, faithful insertion target.
     * Other source modes remain preserved by imports but are rejected before a
     * prompt can silently reinterpret them.
     */
    public static boolean supports(LorebookInsertion insertion) {
        return insertion != null
                && insertion.sourcePosition() == 0
                && insertion.depth() == 0
                && insertion.role() == LorebookPromptRole.SYSTEM
                && insertion.outletName().isBlank();
    }

    private static LorebookPromptRegionName nameFor(int sourcePosition) {
        return switch (sourcePosition) {
            case 0 -> LorebookPromptRegionName.BEFORE_CHARACTER;
            case 1 -> LorebookPromptRegionName.AFTER_CHARACTER;
            case 2 -> LorebookPromptRegionName.AT_DEPTH;
            default -> LorebookPromptRegionName.OUTLET;
        };
    }

    private static int order(LorebookPromptRegionName name) {
        return switch (name) {
            case BEFORE_CHARACTER -> 0;
            case AFTER_CHARACTER -> 1;
            case AT_DEPTH -> 2;
            case OUTLET -> 3;
        };
    }

    private record RegionKey(
            LorebookPromptRegionName name, LorebookPromptRole role, int depth, String outletName) {
    }
}
