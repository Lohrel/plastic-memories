package dev.lohrel.plasticmemories.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

final class NpcProfilesSavedDataTest {
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void roundTripsProfiles() {
        NpcProfilesSavedData data = NpcProfilesSavedData.load(new CompoundTag(), null);
        NpcProfile profile = NpcProfile.create("A cook.", "Cheerful.", "Tall.", "Grew up by the sea.");

        assertTrue(data.putProfile(NPC, profile));
        NpcProfilesSavedData reloaded = NpcProfilesSavedData.load(data.save(new CompoundTag(), null), null);

        assertEquals(profile, reloaded.getProfile(NPC));
    }

    @Test
    void worldSavedByANewerVersionIsWrittenBackUntouchedAndRejectsEdits() {
        CompoundTag future = new CompoundTag();
        future.putInt("Version", 99);
        future.putString("SomethingNew", "keep me");

        NpcProfilesSavedData data = NpcProfilesSavedData.load(future.copy(), null);

        assertFalse(data.putProfile(NPC, NpcProfile.create("Edited.", "", "", "")));
        assertEquals(future, data.save(new CompoundTag(), null));
    }

    @Test
    void entriesThatFailValidationAreKeptOnSave() {
        CompoundTag tooLong = new CompoundTag();
        tooLong.putString("Description", "x".repeat(NpcProfile.MAX_DESCRIPTION_LENGTH + 1));
        tooLong.putString("Personality", "");
        tooLong.putString("Appearance", "");
        tooLong.putString("Backstory", "");
        CompoundTag profiles = new CompoundTag();
        profiles.put(NPC.toString(), tooLong);
        profiles.put("not-a-uuid", new CompoundTag());
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 1);
        saved.put("Profiles", profiles);

        NpcProfilesSavedData data = NpcProfilesSavedData.load(saved.copy(), null);
        CompoundTag written = data.save(new CompoundTag(), null);

        assertEquals(NpcProfile.empty(), data.getProfile(NPC));
        assertEquals(tooLong, written.getCompound("Profiles").getCompound(NPC.toString()));
        assertTrue(written.getCompound("Profiles").contains("not-a-uuid"));
    }

    @Test
    void editingAnUnreadableEntryReplacesIt() {
        CompoundTag profiles = new CompoundTag();
        profiles.put(NPC.toString(), new CompoundTag());
        profiles.getCompound(NPC.toString()).putString("Description", "x".repeat(NpcProfile.MAX_DESCRIPTION_LENGTH + 1));
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 1);
        saved.put("Profiles", profiles);
        NpcProfilesSavedData data = NpcProfilesSavedData.load(saved, null);
        NpcProfile edited = NpcProfile.create("Fixed.", "", "", "");

        data.putProfile(NPC, edited);
        NpcProfilesSavedData reloaded = NpcProfilesSavedData.load(data.save(new CompoundTag(), null), null);

        assertEquals(edited, reloaded.getProfile(NPC));
    }

    /**
     * Frozen copy of the v1 save format. Never edit the fixture: if this test breaks, the change
     * needs a migration, not a new fixture. Add one fixture per released format version.
     */
    @Test
    void version1SavesStillLoad() throws Exception {
        CompoundTag saved = fixture("/compat/npc_profiles_v1.snbt");

        NpcProfilesSavedData data = NpcProfilesSavedData.load(saved, null);

        assertEquals(
                NpcProfile.create("The village cook.", "Warm, a little nosy.", "Flour on her apron.",
                        "Came from the coast after the storm."),
                data.getProfile(NPC));
        assertEquals(NpcProfile.empty(), data.getProfile(UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertTrue(data.putProfile(NPC, NpcProfile.empty()), "a current-format save must be editable");
    }

    @Test
    void unknownFieldsInAProfileSurviveLoadEditAndSave() {
        CompoundTag entry = new CompoundTag();
        entry.putString("Description", "Old.");
        entry.putString("Personality", "");
        entry.putString("Appearance", "");
        entry.putString("Backstory", "");
        entry.putString("Pronouns", "she/her");
        CompoundTag profiles = new CompoundTag();
        profiles.put(NPC.toString(), entry);
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 1);
        saved.put("Profiles", profiles);

        NpcProfilesSavedData data = NpcProfilesSavedData.load(saved, null);
        data.putProfile(NPC, NpcProfile.create("New.", "", "", ""));
        CompoundTag written = data.save(new CompoundTag(), null).getCompound("Profiles").getCompound(NPC.toString());

        assertEquals("New.", written.getString("Description"));
        assertEquals("she/her", written.getString("Pronouns"));
    }

    private static CompoundTag fixture(String resource) throws Exception {
        try (var stream = NpcProfilesSavedDataTest.class.getResourceAsStream(resource)) {
            return TagParser.parseTag(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void entriesThatAreNotCompoundsAreKeptOnSave() {
        CompoundTag profiles = new CompoundTag();
        profiles.putString(NPC.toString(), "hand-edited");
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 1);
        saved.put("Profiles", profiles);

        NpcProfilesSavedData data = NpcProfilesSavedData.load(saved, null);
        CompoundTag written = data.save(new CompoundTag(), null);

        assertEquals("hand-edited", written.getCompound("Profiles").getString(NPC.toString()));
    }
}
