package dev.dynamicdock;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.dynamicdock.glass.GlassBackdrop;
import dev.dynamicdock.glass.GlassPipelines;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.HumanoidArm;
import java.nio.file.Files;

/** Runs only in the separate Fabric test mod; never packaged in the distributed JAR. */
public final class DockClientChecks implements FabricClientGameTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static float hiddenFraction() {
        try { var f = HotbarDock.class.getDeclaredField("slideY"); f.setAccessible(true); return f.getFloat(HotbarDock.INSTANCE); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static final KeyEvent CONFIRM = new KeyEvent(InputConstants.KEY_RETURN, 0, 0);
    private static void assertFrameBottom(ClientGameTestContext ctx, String screenshotName) throws Exception {
        int[] point = ctx.computeOnClient(client -> {
            var scalesField = HotbarDock.class.getDeclaredField("scale"); scalesField.setAccessible(true);
            var centerField = HotbarDock.class.getDeclaredField("dockCenter"); centerField.setAccessible(true);
            float[] scales = (float[])scalesField.get(HotbarDock.INSTANCE);
            var c = DynamicDockClient.config;
            int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
            int selected = client.player.getInventory().getSelectedSlot();
            DockLayout layout = new DockLayout(scales, centerField.getFloat(HotbarDock.INSTANCE),
                    !client.player.getOffhandItem().isEmpty(), client.player.getMainArm() == HumanoidArm.RIGHT,
                    false, c.scale, width, height - 45, c.frames, c.shelf, c.shadow ? c.shadowStrength : 0, 0);
            float s = scales[selected];
            float x = width / 2f + layout.size * (layout.x[selected]
                    + (Math.round(layout.x[selected] - 12) + 12 - layout.x[selected]) * s);
            float y = height - 2 - layout.bottom * layout.size + (-3 + 3.5f * s) * layout.size;
            int guiScale = client.getWindow().getGuiScale();
            return new int[]{(int)Math.floor(x * guiScale), (int)Math.floor(y * guiScale)};
        });
        try (var input = Files.newInputStream(ctx.takeScreenshot(screenshotName)); var image = NativeImage.read(input)) {
            require(point[0] >= 0 && point[0] < image.getWidth() && point[1] >= 0 && point[1] < image.getHeight(), "Frame border is inside the framebuffer");
            int pixel = image.getPixel(point[0], point[1]);
            require((pixel >> 16 & 255) < 40 && (pixel >> 8 & 255) < 40 && (pixel & 255) < 40,
                    "Closing black border is present in the rendered image");
        }
    }
    private static void button(ClientGameTestContext ctx, String label) {
        ctx.runOnClient(client -> {
            Button button = Screens.getWidgets(client.gui.screen()).stream()
                    .filter(w -> w instanceof Button && w.getMessage().getString().equals(label))
                    .map(w -> (Button)w).findFirst().orElseThrow(() -> new AssertionError("Missing button " + label));
            button.onPress(CONFIRM);
        });
        ctx.waitTick();
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        try { runChecks(ctx); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void runChecks(ClientGameTestContext ctx) throws Exception {
        ctx.runOnClient(client -> {
            DynamicDockClient.config = new DockConfig();
            KeyMapping key = KeyMapping.get("key.dynamicdock.settings");
            require(key != null && key.isUnbound(), "Settings binding must start unbound");
            require(key.getCategory().label().getString().equals("Dynamic Dock"), "Translated key category");
            require(RenderSystem.getCompiledPipelineNullable(GlassPipelines.SURFACE) != null, "Glass shader compiled");
            for (int i = 1; i <= 8; i++) {
                require(RenderSystem.getCompiledPipelineNullable(GlassPipelines.horizontal(i)) != null, "Horizontal blur " + i);
                require(RenderSystem.getCompiledPipelineNullable(GlassPipelines.vertical(i)) != null, "Vertical blur " + i);
            }
        });
        ctx.setScreen(() -> new OptionsScreen(new TitleScreen(), net.minecraft.client.Minecraft.getInstance().options));
        button(ctx, "Dynamic Dock");
        ctx.waitForScreen(DockSettingsScreen.class);
        ctx.waitTicks(10);
        ctx.takeScreenshot("settings-default");
        button(ctx, "Dock: ON");
        ctx.runOnClient(client -> require(!DynamicDockClient.config.enabled, "Live dock toggle"));
        ctx.takeScreenshot("settings-disabled-preview");
        button(ctx, "Glass");
        ctx.runOnClient(client -> {
            AbstractSliderButton slider = Screens.getWidgets(client.gui.screen()).stream()
                    .filter(w -> w instanceof AbstractSliderButton && w.getMessage().getString().startsWith("Opacity:"))
                    .map(w -> (AbstractSliderButton)w).findFirst().orElseThrow();
            slider.setFocused(true);
            slider.keyPressed(CONFIRM);
            slider.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, 0, 0));
            require(DynamicDockClient.config.opacity < 224f / 255, "Slider updates config immediately");
        });
        ctx.waitTicks(2);
        ctx.takeScreenshot("settings-glass");
        button(ctx, "Tint");
        ctx.takeScreenshot("settings-tint");
        button(ctx, "Reset to Defaults");
        ctx.runOnClient(client -> require(DynamicDockClient.config.enabled && DynamicDockClient.config.opacity == 224f / 255, "Reset defaults"));
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(OptionsScreen.class);
        ctx.runOnClient(client -> {
            require(Files.exists(DynamicDockClient.CONFIG_FILE), "Config written on Escape");
            require(DockConfig.load(DynamicDockClient.CONFIG_FILE, message -> { throw new AssertionError(message); }).enabled, "Game config round trip");
        });
        try (var world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword[minecraft:damage=800]");
            world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:oak_planks 64");
            world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:ender_pearl 16");
            world.getServer().runCommand("experience add @a 10 levels");
            ctx.waitTicks(5);
            world.getConnection().waitForClientboundPackets();
            ctx.runOnClient(client -> {
                client.options.attackIndicator().set(AttackIndicatorStatus.HOTBAR);
                client.player.getInventory().setSelectedSlot(0);
                client.player.setMainArm(HumanoidArm.RIGHT);
                client.player.getCooldowns().addCooldown(client.player.getInventory().getItem(2), 200);
            });
            ctx.waitTicks(40);
            ctx.takeScreenshot("hud-right-hand");
            ctx.runOnClient(client -> {
                require(!GlassBackdrop.failed() && HotbarDock.shifting(), "Dock renders with glass");
                require(client.player.getCooldowns().getCooldownPercent(client.player.getInventory().getItem(2), 0) > 0, "Cooldown overlay active");
            });
            ctx.runOnClient(client -> {
                DynamicDockClient.config.autoHide = true; DynamicDockClient.config.hideDelay = 1;
                HotbarDock.INSTANCE.reset();
            });
            ctx.waitFor(client -> hiddenFraction() > .95f, 10000);
            ctx.takeScreenshot("hud-auto-hidden");
            ctx.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
            ctx.waitFor(client -> hiddenFraction() < .05f, 10000);
            ctx.runOnClient(client -> {
                DynamicDockClient.config.autoHide = false;
                client.player.setMainArm(HumanoidArm.LEFT);
                client.player.getInventory().setSelectedSlot(8);
                client.player.resetAttackStrengthTicker();
                client.player.getInventory().getItem(1).setPopTime(5);
            });
            ctx.waitTicks(2);
            ctx.takeScreenshot("hud-left-hand-attack-pickup");
            ctx.runOnClient(client -> {
                var c = DynamicDockClient.config;
                c.cornerRadius = 0; c.blur = 0; c.refraction = 0; c.edges = false; c.shadow = false;
                c.tintColor = 0x5080FF; c.tintIntensity = .7f; c.opacity = .65f;
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("hud-square-tinted-clear");
            ctx.runOnClient(client -> {
                var c = DynamicDockClient.config;
                c.cornerRadius = 11; c.blur = 2; c.refraction = 2; c.edges = true; c.edgeIntensity = 2;
                c.shadow = true; c.shadowStrength = 2;
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("hud-capsule-tinted-frosted");
            ctx.runOnClient(client -> {
                DynamicDockClient.config = new DockConfig();
                DynamicDockClient.config.magnification = 0;
                DynamicDockClient.config.scale = 2;
                DynamicDockClient.config.shadow = false;
                client.getWindow().setWindowed(1280, 960);
            });
            ctx.waitTicks(10);
            ctx.runOnClient(client -> {
                client.options.guiScale().set(4); client.resizeGui();
                require(client.getWindow().getGuiScale() >= 3, "Large-window GUI scale");
            });
            ctx.waitTicks(20);
            assertFrameBottom(ctx, "hud-zero-magnification-high-scale");
            ctx.runOnClient(client -> {
                var target = new TextureTarget("Resize regression", 1, 1,
                        client.gameRenderer.mainRenderTarget().getColorTexture().getFormat(), null);
                try {
                    require(!GlassBackdrop.capture(target), "Mismatched framebuffer capture is deferred");
                    require(GlassBackdrop.skipDraws() && !GlassBackdrop.failed(), "Resize does not disable glass");
                } finally { target.destroyBuffers(); }
            });
            ctx.runOnClient(client -> {
                DynamicDockClient.config.magnification = 2.5f;
                DynamicDockClient.config.falloff = 8;
                client.getWindow().setWindowed(640, 480);
            });
            ctx.waitTicks(30);
            ctx.runOnClient(client -> require(!GlassBackdrop.failed() && !GlassBackdrop.skipDraws(), "Glass resumes after resizing"));
            ctx.takeScreenshot("hud-resized-max-magnification");
            ctx.runOnClient(client -> {
                DynamicDockClient.config.enabled = false;
                KeyMapping key = KeyMapping.get("key.dynamicdock.settings");
                key.setKey(client.options.keyInventory.getDefaultKey());
                require(key.same(client.options.keyInventory), "Normal conflict detection");
                key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                KeyMapping.resetMapping(); client.options.save();
                String options = Files.readString(client.gameDirectory.toPath().resolve("options.txt"));
                require(options.contains("key_key.dynamicdock.settings:" + key.saveString()), "Normal key persistence");
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("hud-vanilla-disabled");
            ctx.runOnClient(client -> require(!HotbarDock.shifting(), "Disabled dock restores vanilla rendering"));
            ctx.getInput().pressKey(KeyMapping.get("key.dynamicdock.settings"));
            ctx.waitForScreen(DockSettingsScreen.class);
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitForScreen(null);
            ctx.setScreen(() -> new ChatScreen("", false));
            ctx.getInput().pressKey(KeyMapping.get("key.dynamicdock.settings"));
            ctx.runOnClient(client -> require(client.gui.screen() instanceof ChatScreen, "Binding doesn't interrupt typing"));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitForScreen(null);
            ctx.waitTicks(3);
            ctx.runOnClient(client -> require(client.gui.screen() == null, "No deferred key click after chat"));
            ctx.runOnClient(client -> {
                DynamicDockClient.config = new DockConfig();
                client.options.guiScale().set(2); client.resizeGui();
            });
            ctx.runOnClient(client -> {
                // Force the same fallback state used after a GPU capture failure.
                var f = GlassBackdrop.class.getDeclaredField("failed"); f.setAccessible(true); f.setBoolean(null, true);
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("hud-translucent-fallback");
            var reload = ctx.computeOnClient(client -> client.reloadResourcePacks());
            ctx.waitFor(client -> reload.isDone(), 1200);
            ctx.waitFor(client -> client.gui.overlay() == null, 10000);
            ctx.waitTicks(20);
            ctx.runOnClient(client -> {
                require(RenderSystem.getCompiledPipelineNullable(GlassPipelines.SURFACE) != null, "Glass survives reload");
                require(!GlassBackdrop.failed(), "Capture survives reload");
                GlassBackdrop.close();
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("hud-after-resource-reload");
            ctx.runOnClient(client -> require(!GlassBackdrop.failed(), "Targets recreated after cleanup"));
        }
        ctx.runOnClient(client -> {
            KeyMapping.get("key.dynamicdock.settings").setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            DynamicDockClient.config = new DockConfig(); DynamicDockClient.save();
            GlassBackdrop.close();
        });
        DynamicDockClient.LOGGER.info("Dynamic Dock client checks passed");
    }
}
