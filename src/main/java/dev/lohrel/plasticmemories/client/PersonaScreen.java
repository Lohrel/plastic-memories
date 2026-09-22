package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import dev.lohrel.plasticmemories.persona.Persona;
import dev.lohrel.plasticmemories.persona.PersonaStore;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Saved personas on the left, the selected one's name and description on the right. "Use" makes it
 * the active persona; "Lock to NPC" makes it the persona for the NPC you're talking to.
 */
final class PersonaScreen extends Screen {
    private static final int LIST_WIDTH = 130;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final PersonaStore store;
    private final Optional<LocalLorebookBindingKey> currentNpc;
    private final Optional<String> currentNpcName;
    private List<Persona> personas = List.of();
    private Optional<UUID> activeId = Optional.empty();
    private Optional<UUID> lockedId = Optional.empty();
    private UUID selectedId;
    // Typed but not yet saved, kept across rebuilds.
    private String typedName;
    private String typedDescription;
    private int page;
    private EditBox nameBox;
    private MultiLineEditBox descriptionBox;
    private Component status = Component.literal("Personas are stored only on this computer.");

    PersonaScreen(Screen parent, PersonaStore store, Optional<LocalLorebookBindingKey> currentNpc, Optional<String> currentNpcName) {
        super(Component.literal("Personas"));
        this.parent = parent;
        this.store = store;
        this.currentNpc = currentNpc;
        this.currentNpcName = currentNpcName;
    }

    @Override
    protected void init() {
        // Read everything here; render() runs every frame and must not touch the disk.
        personas = store.list();
        activeId = store.active().map(Persona::id);
        lockedId = currentNpc.flatMap(store::lockedFor).map(Persona::id);
        if (selectedId == null || selected().isEmpty()) {
            selectedId = activeId.or(() -> personas.stream().findFirst().map(Persona::id)).orElse(null);
            typedName = null;
        }
        selected().ifPresent(persona -> {
            if (typedName == null) {
                typedName = persona.name();
                typedDescription = persona.description();
            }
        });

        int rowsPerPage = Math.max(1, (height - 110) / ROW_HEIGHT);
        int pageCount = Math.max(1, (personas.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.min(page, pageCount - 1);
        int y = 40;
        for (Persona persona : personas.subList(page * rowsPerPage, Math.min(personas.size(), (page + 1) * rowsPerPage))) {
            Button row = addRenderableWidget(Button.builder(Component.literal(rowLabel(persona)), button -> select(persona.id()))
                    .bounds(20, y, LIST_WIDTH, 20)
                    .build());
            row.active = !persona.id().equals(selectedId);
            y += ROW_HEIGHT;
        }
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                    .bounds(20, height - 56, 20, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                    .bounds(20 + LIST_WIDTH - 20, height - 56, 20, 20).build()).active = page < pageCount - 1;
        }

        int left = 20 + LIST_WIDTH + 12;
        int editorWidth = width - left - 20;
        nameBox = null;
        descriptionBox = null;
        if (selectedId != null) {
            nameBox = new EditBox(font, left, 52, editorWidth, 18, Component.literal("Name"));
            nameBox.setMaxLength(Persona.MAX_NAME_LENGTH);
            nameBox.setValue(typedName);
            addRenderableWidget(nameBox);
            descriptionBox = new MultiLineEditBox(font, left, 90, editorWidth, Math.max(60, height - 160),
                    Component.literal("Description"), Component.literal("Who you are: appearance, background, how others see you..."));
            descriptionBox.setCharacterLimit(Persona.MAX_DESCRIPTION_LENGTH);
            descriptionBox.setValue(typedDescription);
            addRenderableWidget(descriptionBox);
        }

        int buttonY = height - 28;
        int x = width / 2 - 237;
        addRenderableWidget(Button.builder(Component.literal("New"), button -> createPersona())
                .bounds(x, buttonY, 64, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deletePersona())
                .bounds(x + 68, buttonY, 64, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save"), button -> savePersona())
                .bounds(x + 136, buttonY, 64, 20).build());
        Button use = addRenderableWidget(Button.builder(Component.literal("Use"), button -> usePersona())
                .bounds(x + 204, buttonY, 64, 20).build());
        boolean lockedHere = selectedId != null && lockedId.map(selectedId::equals).orElse(false);
        Button lock = addRenderableWidget(Button.builder(
                        Component.literal(lockedHere ? "Unlock NPC" : "Lock to NPC"), button -> toggleLock(lockedHere))
                .bounds(x + 272, buttonY, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(x + 370, buttonY, 64, 20).build());
        delete.active = selectedId != null;
        save.active = selectedId != null;
        use.active = selectedId != null && !activeId.map(selectedId::equals).orElse(false);
        lock.active = selectedId != null && currentNpc.isPresent();
    }

    private String rowLabel(Persona persona) {
        String marker = activeId.map(persona.id()::equals).orElse(false) ? " (active)"
                : lockedId.map(persona.id()::equals).orElse(false) ? " (this NPC)" : "";
        String label = persona.name() + marker;
        return font.width(label) <= LIST_WIDTH - 8 ? label : font.plainSubstrByWidth(label, LIST_WIDTH - 16) + "…";
    }

    private Optional<Persona> selected() {
        return personas.stream().filter(persona -> persona.id().equals(selectedId)).findFirst();
    }

    private void select(UUID id) {
        selectedId = id;
        typedName = null;
        rebuildWidgets();
    }

    private void changePage(int delta) {
        keepTyped();
        page += delta;
        rebuildWidgets();
    }

    private void createPersona() {
        run(() -> {
            Persona created = store.create("New persona", "");
            selectedId = created.id();
            typedName = null;
        }, "New persona created. Give it a name and description, then Save.");
    }

    private void deletePersona() {
        UUID id = selectedId;
        run(() -> {
            store.delete(id);
            selectedId = null;
            typedName = null;
        }, "Persona deleted.");
    }

    private void savePersona() {
        keepTyped();
        UUID id = selectedId;
        run(() -> {
            store.update(new Persona(id, typedName, typedDescription));
            typedName = null;
        }, "Saved.");
    }

    private void usePersona() {
        UUID id = selectedId;
        run(() -> store.setActive(id), "Now using " + typedOrSavedName() + " with every NPC that has no lock.");
    }

    private void toggleLock(boolean lockedHere) {
        LocalLorebookBindingKey npc = currentNpc.orElseThrow();
        String npcName = currentNpcName.orElse("this NPC");
        UUID id = selectedId;
        if (lockedHere) {
            run(() -> store.unlock(npc), npcName + " now sees your active persona.");
        } else {
            run(() -> store.lock(npc, id), npcName + " will always see you as " + typedOrSavedName() + ".");
        }
    }

    /** Runs a store change, keeping unsaved typing, then rebuilds the screen with a status message. */
    private void run(StoreChange change, String success) {
        keepTyped();
        try {
            change.apply();
            status = Component.literal(success);
        } catch (IOException | IllegalArgumentException exception) {
            status = Component.literal(exception instanceof IllegalArgumentException ? exception.getMessage()
                    : "Could not save personas.");
        }
        rebuildWidgets();
    }

    private void keepTyped() {
        if (nameBox != null && descriptionBox != null) {
            typedName = nameBox.getValue();
            typedDescription = descriptionBox.getValue();
        }
    }

    private String typedOrSavedName() {
        return selected().map(Persona::name).orElse("this persona");
    }

    @FunctionalInterface
    private interface StoreChange {
        void apply() throws IOException;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Same as the other Plastic Memories screens: no background blur.
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawString(font, Component.literal("Saved personas"), 20, 28, 0xBBBBBB, true);
        if (personas.isEmpty()) {
            graphics.drawString(font, Component.literal("None yet: press New."), 20, 44, 0xFFFFFF, true);
        }
        if (nameBox != null) {
            graphics.drawString(font, Component.literal("Name (replaces {{user}} in cards and lore)"),
                    nameBox.getX(), 40, 0xFFFFFF, true);
            graphics.drawString(font, Component.literal("Description"), nameBox.getX(), 78, 0xFFFFFF, true);
        }
        graphics.drawCenteredString(font, status, width / 2, height - 42, 0xBBBBBB);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
