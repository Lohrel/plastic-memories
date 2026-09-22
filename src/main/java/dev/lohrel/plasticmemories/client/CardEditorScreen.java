package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.card.CardField;
import dev.lohrel.plasticmemories.card.CharacterCards;
import dev.lohrel.plasticmemories.lorebook.ImportedCharacterCard;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "My card" view of the profile screen: the player's card for this NPC, field by field. Edits are
 * saved for this NPC only (see {@link CharacterCards#saveEdits}); the card file is never changed.
 */
final class CardEditorScreen extends Screen {
    private final NpcProfileScreen profileScreen;
    private final CharacterCards cards;
    private final LocalLorebookBindingKey npc;
    private final String npcName;
    // What's typed, per field, kept across rebuilds (tab switches, returning from the picker).
    private final Map<CardField, String> working = new EnumMap<>(CardField.class);
    private String workingFile;
    private Optional<CharacterCards.EditableCard> card = Optional.empty();
    private CardField selectedField = CardField.DESCRIPTION;
    private MultiLineEditBox editor;
    private Component status = Component.literal("Only you see this card and your changes to it.");

    CardEditorScreen(NpcProfileScreen profileScreen, CharacterCards cards, LocalLorebookBindingKey npc, String npcName) {
        super(Component.literal("My card: " + npcName));
        this.profileScreen = profileScreen;
        this.cards = cards;
        this.npc = npc;
        this.npcName = npcName;
    }

    @Override
    protected void init() {
        editor = null;
        card = cards.editableCard(npc);
        // A different card was picked (or none): start from its current text.
        String file = card.map(CharacterCards.EditableCard::fileName).orElse(null);
        if (file == null || !file.equals(workingFile)) {
            working.clear();
            card.ifPresent(current -> {
                for (CardField field : CardField.values()) {
                    working.put(field, field.get(current.card()));
                }
            });
            workingFile = file;
        }

        NpcProfileScreen.viewToggle(width, false, () -> switchTo(profileScreen)).forEach(this::addRenderableWidget);

        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Card..."), button -> openPicker())
                .bounds(width / 2 - 188, buttonY, 90, 20)
                .build());
        if (card.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                    .bounds(width / 2 + 98, buttonY, 90, 20)
                    .build());
            return;
        }

        int margin = 20;
        CardField[] fields = CardField.values();
        int tabWidth = Math.max(48, (width - margin * 2 - 3 * (fields.length - 1)) / fields.length);
        int x = margin;
        for (CardField field : fields) {
            Button tab = addRenderableWidget(Button.builder(Component.literal(field.label()), button -> select(field))
                    .bounds(x, 38, tabWidth, 20)
                    .build());
            tab.active = field != selectedField;
            x += tabWidth + 3;
        }

        editor = new MultiLineEditBox(font, margin, 76, width - margin * 2, Math.max(70, height - 130),
                Component.literal(selectedField.label()), Component.literal("Empty"));
        editor.setCharacterLimit(ImportedCharacterCard.MAX_FIELD_LENGTH);
        editor.setValue(working.getOrDefault(selectedField, ""));
        addRenderableWidget(editor);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(width / 2 - 94, buttonY, 90, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset to file"), button -> resetToFile())
                .bounds(width / 2 + 2, buttonY, 90, 20)
                .build()).active = !card.orElseThrow().edited().isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(width / 2 + 98, buttonY, 90, 20)
                .build());
        setInitialFocus(editor);
    }

    private void select(CardField field) {
        keepTyped();
        selectedField = field;
        rebuildWidgets();
    }

    private void save() {
        keepTyped();
        try {
            cards.saveEdits(npc, working);
            status = Component.literal("Saved for " + npcName + ". Only you see these changes.");
        } catch (IOException | IllegalArgumentException exception) {
            status = Component.literal("Could not save the card changes.");
        }
        rebuildWidgets();
    }

    private void resetToFile() {
        try {
            cards.resetEdits(npc);
            workingFile = null;
            status = Component.literal("Back to the card file's text.");
        } catch (IOException exception) {
            status = Component.literal("Could not reset the card.");
        }
        rebuildWidgets();
    }

    private void openPicker() {
        keepTyped();
        if (minecraft != null) {
            minecraft.setScreen(new CardPickerScreen(this, cards, npc, npcName));
        }
    }

    private void switchTo(Screen screen) {
        keepTyped();
        if (minecraft != null) {
            minecraft.setScreen(screen);
        }
    }

    private void keepTyped() {
        if (editor != null && card.isPresent()) {
            working.put(selectedField, editor.getValue());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Same as the other Plastic Memories screens: no background blur.
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (card.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No card picked for " + npcName + ". Press Card... to choose one."),
                    width / 2, height / 2 - 10, 0xFFFFFF);
        } else {
            CharacterCards.EditableCard current = card.orElseThrow();
            boolean edited = editor != null
                    && !editor.getValue().equals(selectedField.get(current.fromFile()));
            graphics.drawString(font, Component.literal(selectedField.label() + " — " + current.card().name()
                    + " (" + current.fileName() + ")" + (edited ? " · edited" : "")), 20, 64, 0xFFFFFF, true);
        }
        graphics.drawCenteredString(font, status, width / 2, height - 42, 0xBBBBBB);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(profileScreen.parent());
        }
    }
}
