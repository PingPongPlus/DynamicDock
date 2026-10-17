package dev.dynamicdock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Vanilla widgets only. The material is confined to the hotbar preview. */
public final class DockSettingsScreen extends Screen {
    private static final String[] PAGES = {"Dock", "Glass", "Tint"};
    private final Screen parent;
    private int page, control;
    public DockSettingsScreen(Screen parent) { super(Component.literal("Dynamic Dock")); this.parent = parent; }
    @Override protected void init() {
        control = 0;
        int total = Math.min(310, width - 12), start = (width - total) / 2;
        for (int i = 0; i < PAGES.length; i++) {
            int target = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(PAGES[i]), button -> {
                page = target; rebuildWidgets();
            }).bounds(start + i * (total / 3), 28, total / 3 - 2, 20).build());
            b.active = page != i;
        }
        DockConfig c = DynamicDockClient.config;
        if (page == 0) {
            toggle("Dock", () -> c.enabled, () -> { c.enabled = !c.enabled; HotbarDock.INSTANCE.reset(); });
            slider("Scale", .5, 2, () -> c.scale, v -> c.scale = (float)v, "%.0f%%", 100);
            slider("Magnification", 0, 2.5, () -> c.magnification, v -> c.magnification = (float)v, "%.0f%%", 100);
            slider("Falloff", .5, 8, () -> c.falloff, v -> c.falloff = (float)v, "%.1f slots", 1);
            toggle("Shelf", () -> c.shelf, () -> c.shelf = !c.shelf);
            toggle("Selection / frame", () -> c.frames, () -> c.frames = !c.frames);
            toggle("Auto-hide", () -> c.autoHide, () -> { c.autoHide = !c.autoHide; HotbarDock.INSTANCE.reset(); });
            slider("Hide delay", 1, 120, () -> c.hideDelay, v -> c.hideDelay = Math.round(v), "%.0f s", 1);
        } else if (page == 1) {
            slider("Opacity", 0, 1, () -> c.opacity, v -> c.opacity = (float)v, "%.0f%%", 100);
            slider("Blur", 0, 2, () -> c.blur, v -> c.blur = Math.round(v * 4) / 4f, "%.0f%%", 100);
            slider("Refraction", 0, 2, () -> c.refraction, v -> c.refraction = (float)v, "%.0f%%", 100);
            slider("Corner radius", 0, 11, () -> c.cornerRadius, v -> c.cornerRadius = (float)v, "%.1f px", 1);
            toggle("Edge highlights", () -> c.edges, () -> c.edges = !c.edges);
            slider("Edge intensity", 0, 2, () -> c.edgeIntensity, v -> c.edgeIntensity = (float)v, "%.0f%%", 100);
            toggle("Shadow", () -> c.shadow, () -> c.shadow = !c.shadow);
            slider("Shadow strength", 0, 2, () -> c.shadowStrength, v -> c.shadowStrength = (float)v, "%.0f%%", 100);
        } else {
            slider("Tint red", 0, 255, () -> (c.tintColor >> 16) & 255, v -> c.tintColor = (c.tintColor & 0x00FFFF) | ((int)Math.round(v) << 16), "%.0f", 1);
            slider("Tint green", 0, 255, () -> (c.tintColor >> 8) & 255, v -> c.tintColor = (c.tintColor & 0xFF00FF) | ((int)Math.round(v) << 8), "%.0f", 1);
            slider("Tint blue", 0, 255, () -> c.tintColor & 255, v -> c.tintColor = (c.tintColor & 0xFFFF00) | (int)Math.round(v), "%.0f", 1);
            slider("Tint intensity", 0, 1, () -> c.tintIntensity, v -> c.tintIntensity = (float)v, "%.0f%%", 100);
        }
        addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), b -> {
            DynamicDockClient.config = new DockConfig(); DynamicDockClient.save();
            HotbarDock.INSTANCE.reset(); rebuildWidgets();
        }).bounds(width / 2 - 155, height - 27, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 5, height - 27, 150, 20).build());
    }
    private int widgetWidth() { return Math.min(150, (width - 22) / 2); }
    private int nextX() { return width / 2 - widgetWidth() - 5 + (control % 2) * (widgetWidth() + 10); }
    private int nextY() { return 54 + (control / 2) * 24; }
    private void toggle(String label, BooleanSupplier get, Runnable toggle) {
        addRenderableWidget(Button.builder(toggleLabel(label, get.getAsBoolean()), b -> {
            toggle.run(); DynamicDockClient.changed(); b.setMessage(toggleLabel(label, get.getAsBoolean()));
        }).bounds(nextX(), nextY(), widgetWidth(), 20).build());
        control++;
    }
    private Component toggleLabel(String label, boolean value) { return Component.literal(label + ": ").append(Component.translatable(value ? "options.on" : "options.off")); }
    private void slider(String label, double min, double max, DoubleSupplier get, DoubleConsumer set, String format, double factor) {
        var slider = new AbstractSliderButton(nextX(), nextY(), widgetWidth(), 20, Component.empty(), (get.getAsDouble() - min) / (max - min)) {
            @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, format, get.getAsDouble() * factor))); }
            @Override protected void applyValue() { set.accept(min + value * (max - min)); DynamicDockClient.changed(); updateMessage(); }
        };
        slider.setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, format, get.getAsDouble() * factor)));
        if (label.equals("Magnification")) slider.setTooltip(Tooltip.create(Component.literal("0% disables all magnification, including the selected-slot boost.")));
        if (label.equals("Corner radius")) slider.setTooltip(Tooltip.create(Component.literal("0 = square; 11 = capsule. Radius follows dock scale.")));
        addRenderableWidget(slider); control++;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        int previewTop = Math.max(151, height - 102);
        g.centeredText(font, Component.literal(DynamicDockClient.config.enabled ? "Hotbar preview" : "Hotbar preview (dock disabled)"), width / 2, previewTop, 0xFFFFFFFF);
        HotbarDock.preview(g, height - 36, Math.max(20, height - 46 - previewTop));
    }
    @Override public void onClose() { DynamicDockClient.save(); minecraft.gui.setScreen(parent); }
    @Override public void removed() { DynamicDockClient.save(); }
}
