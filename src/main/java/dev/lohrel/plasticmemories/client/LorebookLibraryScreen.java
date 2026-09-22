package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.lorebook.ClientLorebookAutoImporter;
import dev.lohrel.plasticmemories.lorebook.ClientLorebookInbox;
import dev.lohrel.plasticmemories.lorebook.ClientLorebookLibraryStore;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-only inbox and activation UI for imported cards and lorebooks. */
public final class LorebookLibraryScreen extends Screen {
    private static final int PAGE_SIZE = 4;

    private final Screen parent;
    private final ClientLorebookLibraryStore library;
    private final ClientLorebookInbox inbox;
    private final Optional<LocalLorebookBindingKey> bindingKey;
    private final int page;
    private Component status;

    public LorebookLibraryScreen(
            Screen parent,
            ClientLorebookLibraryStore library,
            ClientLorebookInbox inbox,
            Optional<LocalLorebookBindingKey> bindingKey) {
        this(parent, library, inbox, bindingKey, 0, Component.empty());
    }

    private LorebookLibraryScreen(
            Screen parent,
            ClientLorebookLibraryStore library,
            ClientLorebookInbox inbox,
            Optional<LocalLorebookBindingKey> bindingKey,
            int page,
            Component status) {
        super(Component.literal("Plastic Memories Lorebook Library"));
        this.parent = parent;
        this.library = library;
        this.inbox = inbox;
        this.bindingKey = bindingKey;
        this.page = page;
        this.status = status;
    }

    @Override
    protected void init() {
        ClientLorebookAutoImporter.ImportReport importReport = ClientLorebookAutoImporter.scanAndImport(
                inbox, library);
        List<ClientLorebookLibraryStore.ArtifactSummary> artifacts = library.listArtifacts();
        int pageCount = Math.max(1, pageCount(artifacts));
        int visiblePage = Math.min(page, pageCount - 1);
        int top = 36;

        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refresh(Component.empty()))
                .bounds(20, top, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Unbind NPC"), button -> unbind())
                .bounds(104, top, 100, 20)
                .build()).active = bindingKey.isPresent();
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("Previous"), button -> changePage(visiblePage - 1, pageCount))
                    .bounds(width - 184, top, 80, 20)
                    .build()).active = visiblePage > 0;
            addRenderableWidget(Button.builder(Component.literal("Next"), button -> changePage(visiblePage + 1, pageCount))
                    .bounds(width - 100, top, 80, 20)
                    .build()).active = visiblePage < pageCount - 1;
        }

        int artifactY = 88;
        for (ClientLorebookLibraryStore.ArtifactSummary artifact : page(artifacts, visiblePage)) {
            String action = artifact.characterCard()
                    ? "Bind"
                    : artifact.globallyActive() ? "Deactivate" : "Activate";
            Button actionButton = addRenderableWidget(Button.builder(Component.literal(action), button -> updateArtifact(artifact))
                    .bounds(width - 188, artifactY, 82, 20)
                    .build());
            actionButton.active = artifact.activationPossible() && (!artifact.characterCard() || bindingKey.isPresent());
            addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeArtifact(artifact.id()))
                    .bounds(width - 102, artifactY, 82, 20)
                    .build());
            artifactY += 24;
        }

        if (status.getString().isBlank()) {
            Component automaticStatus = automaticImportStatus(importReport);
            if (!automaticStatus.getString().isBlank()) {
                status = automaticStatus;
            }
        }
    }

    private void changePage(int requestedPage, int pageCount) {
        refresh(Math.max(0, Math.min(requestedPage, pageCount - 1)), Component.empty());
    }

    private void updateArtifact(ClientLorebookLibraryStore.ArtifactSummary artifact) {
        try {
            if (artifact.characterCard()) {
                library.bindCard(bindingKey.orElseThrow(), artifact.id());
                refresh(Component.literal("Card bound only to the selected local NPC."));
            } else if (artifact.globallyActive()) {
                library.deactivateGlobal(artifact.id());
                refresh(Component.literal("Imported lorebook deactivated for this client."));
            } else {
                library.activateGlobal(artifact.id());
                refresh(Component.literal("Imported lorebook activated only for this client's private prompts."));
            }
        } catch (IOException | IllegalArgumentException exception) {
            refresh(Component.literal("Could not update the local import state."));
        }
    }

    private void unbind() {
        if (bindingKey.isEmpty()) {
            return;
        }
        try {
            library.unbindCard(bindingKey.orElseThrow());
            refresh(Component.literal("Any local card binding for the selected NPC was removed."));
        } catch (IOException exception) {
            refresh(Component.literal("Could not update the local card binding."));
        }
    }

    private void removeArtifact(UUID id) {
        try {
            if (library.remove(id)) {
                refresh(Component.literal("Local artifact removed and its local activations revoked."));
            } else {
                refresh(Component.literal("The local artifact no longer exists."));
            }
        } catch (IOException exception) {
            refresh(Component.literal("Could not remove the local artifact."));
        }
    }

    private void refresh(Component nextStatus) {
        refresh(page, nextStatus);
    }

    private void refresh(int nextPage, Component nextStatus) {
        if (minecraft != null) {
            minecraft.setScreen(new LorebookLibraryScreen(
                    parent, library, inbox, bindingKey, nextPage, nextStatus));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawString(font, Component.literal("Drop files in the inbox; they import automatically."), 20, 62, 0xBBBBBB, true);
        graphics.drawString(font, Component.literal("Stored lorebooks"), 20, 76, 0xFFFFFF, true);
        drawArtifacts(graphics);
        graphics.drawCenteredString(font, status, width / 2, height - 24, 0xFFBBBBBB);
    }

    private void drawArtifacts(GuiGraphics graphics) {
        List<ClientLorebookLibraryStore.ArtifactSummary> artifacts = library.listArtifacts();
        int visiblePage = Math.min(page, Math.max(0, pageCount(artifacts) - 1));
        int y = 88;
        for (ClientLorebookLibraryStore.ArtifactSummary artifact : page(artifacts, visiblePage)) {
            String state = artifact.globallyActive()
                    ? "active"
                    : artifact.boundToAnyCard() ? "bound" : "inactive";
            if (!artifact.activationPossible()) {
                state = "unavailable";
            }
            graphics.drawString(
                    font,
                    Component.literal(abbreviated(artifact.sourceFilename()) + " · " + state),
                    20,
                    y + 6,
                    0xFFFFFF,
                    true);
            y += 24;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static Component automaticImportStatus(ClientLorebookAutoImporter.ImportReport report) {
        if (report.importedCount() > 0) {
            return Component.literal("Imported " + report.importedCount() + " new file(s).");
        }
        if (report.rejectedCount() > 0) {
            return Component.literal("Skipped " + report.rejectedCount() + " unsupported file(s).");
        }
        if (report.failedCount() > 0) {
            return Component.literal("Could not save " + report.failedCount() + " file(s).");
        }
        return Component.empty();
    }

    private static <T> List<T> page(List<T> values, int page) {
        int from = Math.min(values.size(), Math.max(0, page) * PAGE_SIZE);
        int to = Math.min(values.size(), from + PAGE_SIZE);
        return values.subList(from, to);
    }

    private static int pageCount(List<?> values) {
        return Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static String abbreviated(String value) {
        return value.length() <= 34 ? value : value.substring(0, 31) + "...";
    }
}
