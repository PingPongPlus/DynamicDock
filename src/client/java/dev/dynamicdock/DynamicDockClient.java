package dev.dynamicdock;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dynamicdock.glass.GlassBackdrop;
import dev.dynamicdock.glass.GlassPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class DynamicDockClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dynamicdock");
    public static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("dynamicdock.properties");
    public static DockConfig config = DockConfig.load(CONFIG_FILE, LOGGER::warn);
    private static KeyMapping settingsKey;
    private static long saveAt;
    public static void changed() { config.validate(); saveAt = System.nanoTime() + 500_000_000L; }
    public static void save() { config.save(CONFIG_FILE, LOGGER::warn); saveAt = 0; }

    @Override public void onInitializeClient() {
        GlassPipelines.initialize();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dynamicdock", "settings"));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.dynamicdock.settings",
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Always drain clicks: a press in chat or another screen must never open settings later.
            while (settingsKey.consumeClick()) {
                if (client.gui.screen() == null && client.player != null) client.gui.setScreen(new DockSettingsScreen(null));
            }
            if (saveAt != 0 && System.nanoTime() >= saveAt) save();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof OptionsScreen) Screens.getWidgets(screen).add(
                    Button.builder(Component.literal("Dynamic Dock"), button -> client.gui.setScreen(new DockSettingsScreen(screen)))
                            .bounds(width - 108, 6, 102, 20).build());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { save(); GlassBackdrop.close(); });
    }
}
