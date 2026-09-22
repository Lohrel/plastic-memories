package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.network.NpcProfileResponse;
import dev.lohrel.plasticmemories.network.NpcProfileResultCode;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tabbed view/editor for an NPC's shared profile. Read-only unless the server said this player may edit. */
public final class NpcProfileScreen extends Screen {
    private final Screen parent;
    private final String npcName;
    private final boolean canEdit;
    private final BiConsumer<NpcProfileScreen, NpcProfile> saveHandler;
    private String description;
    private String personality;
    private String appearance;
    private String backstory;
    private ProfileField selectedField = ProfileField.DESCRIPTION;
    private MultiLineEditBox editor;
    private Button saveButton;
    private Component status;

    public NpcProfileScreen(
            Screen parent,
            String npcName,
            NpcProfile profile,
            boolean canEdit,
            BiConsumer<NpcProfileScreen, NpcProfile> saveHandler) {
        super(Component.literal("Character Profile: " + npcName));
        this.parent = parent;
        this.npcName = npcName;
        this.canEdit = canEdit;
        this.saveHandler = saveHandler;
        description = profile.description();
        personality = profile.personality();
        appearance = profile.appearance();
        backstory = profile.backstory();
        status = canEdit
                ? Component.literal("Shared server profile")
                : Component.literal("Read-only: only server operators or the LAN host can edit");
    }

    @Override
    protected void init() {
        int margin = 20;
        int tabY = 38;
        int tabWidth = Math.max(60, (width - margin * 2 - 9) / 4);
        int x = margin;
        for (ProfileField field : ProfileField.values()) {
            addRenderableWidget(Button.builder(Component.literal(field.label), button -> select(field))
                    .bounds(x, tabY, tabWidth, 20)
                    .build());
            x += tabWidth + 3;
        }

        int editorHeight = Math.max(70, height - 130);
        editor = new MultiLineEditBox(
                font,
                margin,
                76,
                width - margin * 2,
                editorHeight,
                Component.literal(selectedField.label),
                Component.literal("Enter " + selectedField.label.toLowerCase()));
        editor.setCharacterLimit(selectedField.maximumLength);
        editor.setValue(value(selectedField));
        editor.active = canEdit;
        addRenderableWidget(editor);

        int buttonY = height - 28;
        saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(width / 2 - 104, buttonY, 100, 20)
                .build());
        saveButton.active = canEdit;
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(width / 2 + 4, buttonY, 100, 20)
                .build());
        setInitialFocus(editor);
    }

    private void select(ProfileField field) {
        storeSelectedValue();
        selectedField = field;
        editor.setCharacterLimit(field.maximumLength);
        editor.setValue(value(field));
    }

    private void save() {
        storeSelectedValue();
        try {
            NpcProfile profile = NpcProfile.create(description, personality, appearance, backstory);
            saveButton.active = false;
            status = Component.literal("Saving shared profile...");
            saveHandler.accept(this, profile);
        } catch (IllegalArgumentException exception) {
            status = Component.literal(exception.getMessage());
        }
    }

    public void handleResponse(NpcProfileResponse response) {
        if (response.result() == NpcProfileResultCode.SUCCESS) {
            NpcProfile profile = response.profile();
            description = profile.description();
            personality = profile.personality();
            appearance = profile.appearance();
            backstory = profile.backstory();
            editor.setValue(value(selectedField));
            status = Component.literal("Shared profile saved.");
        } else {
            status = Component.literal(message(response.result()));
        }
        saveButton.active = canEdit;
    }

    public void handleFailure() {
        status = Component.literal("Profile request failed.");
        saveButton.active = canEdit;
    }

    private void storeSelectedValue() {
        String value = editor.getValue();
        switch (selectedField) {
            case DESCRIPTION -> description = value;
            case PERSONALITY -> personality = value;
            case APPEARANCE -> appearance = value;
            case BACKSTORY -> backstory = value;
        }
    }

    private String value(ProfileField field) {
        return switch (field) {
            case DESCRIPTION -> description;
            case PERSONALITY -> personality;
            case APPEARANCE -> appearance;
            case BACKSTORY -> backstory;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawString(font, Component.literal(selectedField.label + " — " + npcName), 20, 64, 0xFFFFFF, true);
        graphics.drawCenteredString(font, status, width / 2, height - 42, 0xBBBBBB);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static String message(NpcProfileResultCode result) {
        return switch (result) {
            case SUCCESS -> "Shared profile saved.";
            case INVALID_REQUEST -> "Profile update was malformed.";
            case INVALID_NPC -> "The selected NPC is unavailable.";
            case OUT_OF_RANGE -> "The selected NPC is too far away.";
            case PERMISSION_DENIED -> "Only server operators or the LAN host can edit this profile.";
            case RATE_LIMITED -> "Wait before saving again.";
            case REPLAYED -> "Duplicate profile update rejected.";
        };
    }

    private enum ProfileField {
        DESCRIPTION("Description", NpcProfile.MAX_DESCRIPTION_LENGTH),
        PERSONALITY("Personality", NpcProfile.MAX_PERSONALITY_LENGTH),
        APPEARANCE("Appearance", NpcProfile.MAX_APPEARANCE_LENGTH),
        BACKSTORY("Backstory", NpcProfile.MAX_BACKSTORY_LENGTH);

        private final String label;
        private final int maximumLength;

        ProfileField(String label, int maximumLength) {
            this.label = label;
            this.maximumLength = maximumLength;
        }
    }
}
