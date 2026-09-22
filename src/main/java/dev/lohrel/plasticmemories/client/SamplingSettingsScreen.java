package dev.lohrel.plasticmemories.client;

import dev.lohrel.plasticmemories.provider.SamplingParameter;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Optional sampling values, in two columns. Blank means "use the provider's default". */
final class SamplingSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 36;

    private final Screen parent;
    private final Map<SamplingParameter, Double> initial;
    private final Consumer<Map<SamplingParameter, Double>> onDone;
    private final EnumMap<SamplingParameter, EditBox> fields = new EnumMap<>(SamplingParameter.class);
    private Component status = Component.empty();

    SamplingSettingsScreen(
            Screen parent, Map<SamplingParameter, Double> initial, Consumer<Map<SamplingParameter, Double>> onDone) {
        super(Component.literal("Sampling settings"));
        this.parent = parent;
        this.initial = initial;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int columnWidth = Math.min(200, (width - 60) / 2);
        int left = width / 2 - columnWidth - 10;
        SamplingParameter[] parameters = SamplingParameter.values();
        int rows = (parameters.length + 1) / 2;
        for (int index = 0; index < parameters.length; index++) {
            SamplingParameter parameter = parameters[index];
            int x = index < rows ? left : width / 2 + 10;
            int y = 56 + (index % rows) * ROW_HEIGHT;
            EditBox field = new EditBox(font, x, y, columnWidth, 18, Component.literal(parameter.label()));
            field.setMaxLength(16);
            field.setHint(Component.literal("default (" + parameter.rangeHint() + ")"));
            Double value = initial.get(parameter);
            field.setValue(value == null ? "" : parameter.format(value));
            fields.put(parameter, field);
            addRenderableWidget(field);
        }
        int buttonsY = 56 + rows * ROW_HEIGHT + 8;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> done())
                .bounds(width / 2 - 104, buttonsY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 4, buttonsY, 100, 20)
                .build());
    }

    private void done() {
        EnumMap<SamplingParameter, Double> values = new EnumMap<>(SamplingParameter.class);
        try {
            fields.forEach((parameter, field) -> parameter.parse(field.getValue())
                    .ifPresent(value -> values.put(parameter, value)));
        } catch (IllegalArgumentException exception) {
            status = Component.literal(exception.getMessage());
            return;
        }
        onDone.accept(values);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Same as ProviderSettingsScreen: no background blur.
        graphics.fill(0, 0, width, height, 0xF0181818);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Leave a field blank to use the provider's default."),
                width / 2, 34, 0xBBBBBB);
        fields.forEach((parameter, field) ->
                graphics.drawString(font, Component.literal(parameter.label()), field.getX(), field.getY() - 11, 0xFFFFFF, true));
        graphics.drawCenteredString(font, status, width / 2, height - 20, 0xFF7777);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
