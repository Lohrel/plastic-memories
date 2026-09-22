package dev.lohrel.plasticmemories.server;

import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Saves shared character profiles with the world. Always stored in the overworld so all dimensions share one copy. */
public final class NpcProfilesSavedData extends SavedData {
    private static final int FORMAT_VERSION = 1;
    private static final String DATA_NAME = "plastic_memories_npc_profiles";
    private static final Factory<NpcProfilesSavedData> FACTORY = new Factory<>(
            NpcProfilesSavedData::new,
            NpcProfilesSavedData::load,
            DataFixTypes.LEVEL);

    private final NpcProfileRegistry profiles = new NpcProfileRegistry();

    public static NpcProfilesSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public NpcProfile getProfile(UUID npcId) {
        return profiles.get(npcId);
    }

    public void putProfile(UUID npcId, NpcProfile profile) {
        profiles.put(npcId, profile);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", FORMAT_VERSION);
        CompoundTag savedProfiles = new CompoundTag();
        profiles.entries().forEach((npcId, profile) -> {
            CompoundTag savedProfile = new CompoundTag();
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
        // Unknown format: start empty instead of guessing. Needs a migration when FORMAT_VERSION changes.
        if (tag.getInt("Version") != FORMAT_VERSION || !tag.contains("Profiles", Tag.TAG_COMPOUND)) {
            return data;
        }
        CompoundTag savedProfiles = tag.getCompound("Profiles");
        for (String key : savedProfiles.getAllKeys()) {
            try {
                UUID npcId = UUID.fromString(key);
                CompoundTag savedProfile = savedProfiles.getCompound(key);
                data.profiles.put(npcId, NpcProfile.create(
                        savedProfile.getString("Description"),
                        savedProfile.getString("Personality"),
                        savedProfile.getString("Appearance"),
                        savedProfile.getString("Backstory")));
            } catch (IllegalArgumentException ignored) {
                // Bad UUID or a field over the length limit: drop just this entry.
            }
        }
        return data;
    }
}
