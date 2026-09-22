package dev.lohrel.plasticmemories.server;

import com.mojang.logging.LogUtils;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Saves shared character profiles with the world. Always stored in the overworld so all dimensions
 * share one copy.
 *
 * <p>Compatibility rules:
 * <ul>
 *   <li>Older saves are upgraded by {@link #migrate}; the v1 fixture test keeps them loading forever.</li>
 *   <li>Adding an optional field needs no version bump: unknown fields are carried through load and save,
 *       so an older build won't strip them either.</li>
 *   <li>Renaming, removing or changing the meaning of a field needs a version bump and a migration.</li>
 *   <li>A save from a newer, unknown version is kept byte-for-byte and made read-only.</li>
 *   <li>Entries that fail validation are written back unchanged.</li>
 * </ul>
 */
public final class NpcProfilesSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FORMAT_VERSION = 1;
    private static final String DATA_NAME = "plastic_memories_npc_profiles";
    private static final Factory<NpcProfilesSavedData> FACTORY = new Factory<>(
            NpcProfilesSavedData::new,
            NpcProfilesSavedData::load,
            // No vanilla data fixer: those are written for vanilla structures, not ours.
            null);

    private final NpcProfileRegistry profiles = new NpcProfileRegistry();
    /** Raw entries that failed validation, by key, so they survive a save. */
    private final Map<String, Tag> unreadableEntries = new LinkedHashMap<>();
    /** Raw tag of each loaded profile, so fields this build doesn't know about survive a save. */
    private final Map<UUID, CompoundTag> rawEntries = new LinkedHashMap<>();
    /** Set when the save uses a format version this build doesn't understand. */
    private CompoundTag unrecognizedFormat;

    public static NpcProfilesSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public NpcProfile getProfile(UUID npcId) {
        return profiles.get(npcId);
    }

    /** Returns false (and changes nothing) when the data is read-only because of an unknown format version. */
    public boolean putProfile(UUID npcId, NpcProfile profile) {
        if (unrecognizedFormat != null) {
            return false;
        }
        profiles.put(npcId, profile);
        unreadableEntries.remove(npcId.toString());
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unrecognizedFormat != null) {
            return unrecognizedFormat.copy();
        }
        tag.putInt("Version", FORMAT_VERSION);
        CompoundTag savedProfiles = new CompoundTag();
        unreadableEntries.forEach((key, entry) -> savedProfiles.put(key, entry.copy()));
        profiles.entries().forEach((npcId, profile) -> {
            CompoundTag savedProfile = rawEntries.containsKey(npcId) ? rawEntries.get(npcId).copy() : new CompoundTag();
            savedProfile.putString("Description", profile.description());
            savedProfile.putString("Personality", profile.personality());
            savedProfile.putString("Appearance", profile.appearance());
            savedProfile.putString("Backstory", profile.backstory());
            savedProfiles.put(npcId.toString(), savedProfile);
        });
        tag.put("Profiles", savedProfiles);
        return tag;
    }

    static NpcProfilesSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcProfilesSavedData data = new NpcProfilesSavedData();
        if (tag.isEmpty()) {
            return data;
        }
        int version = tag.getInt("Version");
        if (version >= 1 && version < FORMAT_VERSION) {
            try {
                tag = migrate(tag, version);
                version = FORMAT_VERSION;
            } catch (RuntimeException exception) {
                LOGGER.error("Plastic Memories: could not upgrade NPC profile data from format version {}.", version, exception);
            }
        }
        if (version != FORMAT_VERSION) {
            LOGGER.warn("Plastic Memories: NPC profile data has format version {}, this build supports {}."
                    + " Profiles are read-only until the mod is updated.", version, FORMAT_VERSION);
            data.unrecognizedFormat = tag.copy();
            return data;
        }
        CompoundTag savedProfiles = tag.getCompound("Profiles");
        for (String key : savedProfiles.getAllKeys()) {
            if (savedProfiles.getTagType(key) != Tag.TAG_COMPOUND) {
                data.unreadableEntries.put(key, savedProfiles.get(key).copy());
                continue;
            }
            CompoundTag savedProfile = savedProfiles.getCompound(key);
            try {
                UUID npcId = UUID.fromString(key);
                data.profiles.put(npcId, NpcProfile.create(
                        savedProfile.getString("Description"),
                        savedProfile.getString("Personality"),
                        savedProfile.getString("Appearance"),
                        savedProfile.getString("Backstory")));
                data.rawEntries.put(npcId, savedProfile.copy());
            } catch (IllegalArgumentException exception) {
                // Bad UUID or a field over the length limit: keep it as-is rather than losing it.
                data.unreadableEntries.put(key, savedProfile.copy());
            }
        }
        return data;
    }

    /**
     * Upgrades a save one version at a time up to FORMAT_VERSION. When bumping FORMAT_VERSION, add a
     * case for the old version and a fixture test for it; never edit an existing case.
     */
    static CompoundTag migrate(CompoundTag tag, int fromVersion) {
        CompoundTag upgraded = tag.copy();
        for (int version = fromVersion; version < FORMAT_VERSION; version++) {
            // Each step returns the tag in the next version's format, including its new "Version".
            switch (version) {
                // case 1 -> upgraded = migrateV1ToV2(upgraded);
                default -> throw new IllegalStateException("No migration from format version " + version);
            }
        }
        return upgraded;
    }
}
