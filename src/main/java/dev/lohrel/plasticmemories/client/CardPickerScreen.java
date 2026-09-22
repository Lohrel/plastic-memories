package dev.lohrel.plasticmemories.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lohrel.plasticmemories.PlasticMemories;
import dev.lohrel.plasticmemories.card.CharacterCardFolder;
import dev.lohrel.plasticmemories.card.CharacterCards;
import dev.lohrel.plasticmemories.lorebook.LocalLorebookBindingKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

/**
 * Grid of the cards in the character-cards folder, with portraits. Clicking one makes it this
 * player's card for the NPC. Only this client knows the choice.
 */
final class CardPickerScreen extends Screen {
    // Portraits are shown at 2:3, the usual character-card shape; thumbnails are uploaded at 2x for sharpness.
    private static final int PORTRAIT_WIDTH = 64;
    private static final int PORTRAIT_HEIGHT = 96;
    private static final int CELL_WIDTH = 76;
    private static final int CELL_HEIGHT = PORTRAIT_HEIGHT + 22;
    private static final int GRID_TOP = 40;
    private static final int GRID_BOTTOM_MARGIN = 58;

    private final Screen parent;
    private final CharacterCards cards;
    private final LocalLorebookBindingKey npc;
    private final String npcName;
    private final Map<String, ResourceLocation> portraits = new HashMap<>();
    private List<CharacterCardFolder.CardFile> allCards = List.of();
    private int page;
    private boolean portraitsStale = true;
    // Read once per rebuild; render() runs every frame and must not touch the disk.
    private Optional<String> boundFile = Optional.empty();
    private Component status = Component.empty();

    CardPickerScreen(Screen parent, CharacterCards cards, LocalLorebookBindingKey npc, String npcName) {
        super(Component.literal("Character card for " + npcName));
        this.parent = parent;
        this.cards = cards;
        this.npc = npc;
        this.npcName = npcName;
    }

    @Override
    protected void init() {
        allCards = cards.folder().list();
        boundFile = cards.boundFile(npc);
        page = Math.min(page, pageCount() - 1);
        // Clicking a card rebuilds the widgets too; only decode images when the page or files changed.
        if (portraitsStale) {
            loadPortraits();
            portraitsStale = false;
        }

        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("No card"), button -> choose(null))
                .bounds(width / 2 - 206, buttonY, 80, 20)
                .build()).active = boundFile.isPresent();
        addRenderableWidget(Button.builder(Component.literal("Open folder"), button -> openFolder())
                .bounds(width / 2 - 122, buttonY, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(width / 2 - 38, buttonY, 20, 20)
                .build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(width / 2 + 18, buttonY, 20, 20)
                .build()).active = page < pageCount() - 1;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> reload())
                .bounds(width / 2 + 42, buttonY, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 126, buttonY, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Same as the other Plastic Memories screens: no background blur.
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Only you see the card you pick."), width / 2, 26, 0xBBBBBB);

        if (allCards.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.literal("No cards yet. Put PNG, JSON or .charx cards in config/plastic_memories/character-cards/."),
                    width / 2, height / 2 - 10, 0xFFFFFF);
        }
        List<CharacterCardFolder.CardFile> visible = visibleCards();
        for (int index = 0; index < visible.size(); index++) {
            CharacterCardFolder.CardFile card = visible.get(index);
            int x = cellX(index);
            int y = cellY(index);
            boolean selected = boundFile.map(card.fileName()::equals).orElse(false);
            boolean hovered = mouseX >= x && mouseX < x + PORTRAIT_WIDTH && mouseY >= y && mouseY < y + PORTRAIT_HEIGHT;
            int border = selected ? 0xFF55FF55 : hovered ? 0xFFFFFFFF : 0xFF444444;
            graphics.fill(x - 2, y - 2, x + PORTRAIT_WIDTH + 2, y + PORTRAIT_HEIGHT + 2, border);
            ResourceLocation portrait = portraits.get(card.fileName());
            if (portrait != null) {
                graphics.blit(portrait, x, y, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, 0, 0,
                        PORTRAIT_WIDTH * 2, PORTRAIT_HEIGHT * 2, PORTRAIT_WIDTH * 2, PORTRAIT_HEIGHT * 2);
            } else {
                // JSON cards have no image: show the initial instead.
                graphics.fill(x, y, x + PORTRAIT_WIDTH, y + PORTRAIT_HEIGHT, 0xFF2A2A3A);
                String initial = card.card().name().isBlank() ? "?" : card.card().name().substring(0, 1).toUpperCase(Locale.ROOT);
                graphics.drawCenteredString(font, initial, x + PORTRAIT_WIDTH / 2, y + PORTRAIT_HEIGHT / 2 - 4, 0xFFFFFF);
            }
            graphics.drawCenteredString(font, fitted(card.card().name()), x + PORTRAIT_WIDTH / 2, y + PORTRAIT_HEIGHT + 6,
                    selected ? 0x55FF55 : 0xFFFFFF);
        }
        if (pageCount() > 1) {
            graphics.drawCenteredString(font, (page + 1) + "/" + pageCount(), width / 2, height - 22, 0xBBBBBB);
        }
        graphics.drawCenteredString(font, status, width / 2, height - 44, 0xBBBBBB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<CharacterCardFolder.CardFile> visible = visibleCards();
            for (int index = 0; index < visible.size(); index++) {
                int x = cellX(index);
                int y = cellY(index);
                if (mouseX >= x && mouseX < x + PORTRAIT_WIDTH && mouseY >= y && mouseY < y + CELL_HEIGHT) {
                    choose(visible.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(CharacterCardFolder.CardFile card) {
        try {
            if (card == null) {
                cards.unbind(npc);
                status = Component.literal(npcName + " has no card now.");
            } else {
                cards.bind(npc, card.fileName());
                status = Component.literal(npcName + " now uses " + card.card().name() + ".");
            }
        } catch (IOException exception) {
            status = Component.literal("Could not save the card choice.");
        }
        rebuildWidgets();
    }

    private void openFolder() {
        try {
            Files.createDirectories(cards.folder().directory());
            Util.getPlatform().openFile(cards.folder().directory().toFile());
        } catch (IOException exception) {
            status = Component.literal("Could not create the character-cards folder.");
        }
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        reload();
    }

    private void reload() {
        portraitsStale = true;
        rebuildWidgets();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        // A new size can change how many cards fit on a page.
        portraitsStale = true;
        super.resize(minecraft, width, height);
    }

    /** Decodes this page's portraits into small textures; the previous page's are released first. */
    private void loadPortraits() {
        releasePortraits();
        if (minecraft == null) {
            return;
        }
        for (CharacterCardFolder.CardFile card : visibleCards()) {
            Optional<byte[]> bytes = cards.folder().readPortrait(card.fileName());
            if (bytes.isEmpty()) {
                continue;
            }
            try (NativeImage source = readRgba(bytes.orElseThrow())) {
                NativeImage thumbnail = new NativeImage(PORTRAIT_WIDTH * 2, PORTRAIT_HEIGHT * 2, false);
                // Crop the middle of the image to 2:3, then scale it down to the thumbnail.
                int cropWidth = Math.min(source.getWidth(), source.getHeight() * 2 / 3);
                int cropHeight = Math.min(source.getHeight(), source.getWidth() * 3 / 2);
                source.resizeSubRectTo(
                        (source.getWidth() - cropWidth) / 2, (source.getHeight() - cropHeight) / 2,
                        Math.max(1, cropWidth), Math.max(1, cropHeight), thumbnail);
                ResourceLocation location = minecraft.getTextureManager().register(
                        PlasticMemories.MOD_ID + "_card", new DynamicTexture(thumbnail));
                portraits.put(card.fileName(), location);
            } catch (IOException | RuntimeException exception) {
                // Not a decodable image: the placeholder is shown instead.
            }
        }
    }

    /** Many card PNGs have no alpha channel; force RGBA so resizing into the RGBA thumbnail works. */
    private static NativeImage readRgba(byte[] bytes) throws IOException {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes).rewind();
            return NativeImage.read(NativeImage.Format.RGBA, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private void releasePortraits() {
        if (minecraft != null) {
            portraits.values().forEach(minecraft.getTextureManager()::release);
        }
        portraits.clear();
    }

    @Override
    public void removed() {
        releasePortraits();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private List<CharacterCardFolder.CardFile> visibleCards() {
        int perPage = columns() * rows();
        int from = Math.min(allCards.size(), page * perPage);
        return new ArrayList<>(allCards.subList(from, Math.min(allCards.size(), from + perPage)));
    }

    private int columns() {
        return Math.max(1, (width - 40) / CELL_WIDTH);
    }

    private int rows() {
        return Math.max(1, (height - GRID_TOP - GRID_BOTTOM_MARGIN) / CELL_HEIGHT);
    }

    private int pageCount() {
        int perPage = columns() * rows();
        return Math.max(1, (allCards.size() + perPage - 1) / perPage);
    }

    private int cellX(int index) {
        int gridWidth = columns() * CELL_WIDTH;
        return (width - gridWidth) / 2 + (index % columns()) * CELL_WIDTH + (CELL_WIDTH - PORTRAIT_WIDTH) / 2;
    }

    private int cellY(int index) {
        return GRID_TOP + 6 + (index / columns()) * CELL_HEIGHT;
    }

    private String fitted(String name) {
        return font.width(name) <= CELL_WIDTH - 4 ? name : font.plainSubstrByWidth(name, CELL_WIDTH - 12) + "…";
    }
}
