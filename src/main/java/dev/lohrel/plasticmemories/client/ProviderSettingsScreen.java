package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.provider.ProviderSettings;
import dev.lohrel.plasticmemories.provider.ProviderSettingsStore;
import dev.lohrel.plasticmemories.provider.SamplingParameter;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Form for the provider endpoint, model and API key. */
public final class ProviderSettingsScreen extends Screen {
    private final Screen parent;
    private final ProviderSettingsStore store;
    private final Optional<ProviderSettings> initialSettings;
    private EditBox endpoint;
    private EditBox model;
    private EditBox apiKey;
    private Component status = Component.empty();
    // Edited on the sampling sub-screen; saved together with the rest.
    private Map<SamplingParameter, Double> sampling;
    private String[] pendingText;

    public ProviderSettingsScreen(
            Screen parent, ProviderSettingsStore store, Optional<ProviderSettings> initialSettings) {
        super(Component.literal("Plastic Memories Provider"));
        this.parent = parent;
        this.store = store;
        this.initialSettings = initialSettings;
        this.sampling = initialSettings.map(ProviderSettings::sampling).orElse(Map.of());
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(420, width - 40);
        int left = (width - fieldWidth) / 2;

        endpoint = new EditBox(font, left, 62, fieldWidth, 20, Component.literal("API base URL"));
        endpoint.setMaxLength(ProviderSettings.MAX_ENDPOINT_LENGTH);
        endpoint.setValue(initialSettings.map(value -> value.endpoint().toString()).orElse(""));
        addRenderableWidget(endpoint);

        model = new EditBox(font, left, 105, fieldWidth, 20, Component.literal("Model"));
        model.setMaxLength(ProviderSettings.MAX_MODEL_LENGTH);
        model.setValue(initialSettings.map(ProviderSettings::model).orElse(""));
        addRenderableWidget(model);

        apiKey = new EditBox(font, left, 148, fieldWidth, 20, Component.literal("API key"));
        apiKey.setMaxLength(ProviderSettings.MAX_API_KEY_LENGTH);
        apiKey.setValue(initialSettings.map(ProviderSettings::apiKey).orElse(""));
        apiKey.setFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        addRenderableWidget(apiKey);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(width / 2 - 158, 195, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Sampling..."), button -> openSampling())
                .bounds(width / 2 - 50, 195, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 58, 195, 100, 20)
                .build());
        if (pendingText != null) {
            endpoint.setValue(pendingText[0]);
            model.setValue(pendingText[1]);
            apiKey.setValue(pendingText[2]);
            pendingText = null;
        }
        setInitialFocus(endpoint);
    }

    private void openSampling() {
        if (minecraft == null) {
            return;
        }
        // init() runs again on return and would reset the text boxes, so keep what was typed.
        String typedEndpoint = endpoint.getValue();
        String typedModel = model.getValue();
        String typedKey = apiKey.getValue();
        minecraft.setScreen(new SamplingSettingsScreen(this, sampling, values -> sampling = values));
        pendingText = new String[] {typedEndpoint, typedModel, typedKey};
    }

    private void save() {
        try {
            ProviderSettings settings = ProviderSettings.create(endpoint.getValue(), model.getValue(), apiKey.getValue())
                    .withSampling(sampling);
            store.save(settings);
            onClose();
        } catch (IllegalArgumentException exception) {
            status = Component.literal(exception.getMessage());
        } catch (IOException exception) {
            status = Component.literal("Could not save provider settings.");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Skip the background blur: it distorted the HUD and blurred this screen's text on some renderers.
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = endpoint.getX();
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawString(font, Component.literal("API base URL or full chat completions URL"), left, 50, 0xFFFFFF, true);
        graphics.drawString(font, Component.literal("Model"), left, 93, 0xFFFFFF, true);
        graphics.drawString(
                font,
                Component.literal("API key (stored only on this client)"),
                left,
                136,
                0xFFFFFF,
                true);
        graphics.drawCenteredString(font, status, width / 2, 174, 0xFF7777);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
