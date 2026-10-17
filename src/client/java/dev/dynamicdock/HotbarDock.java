package dev.dynamicdock;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.dynamicdock.glass.GlassSurface;

/** macOS-style dock hotbar: the selected slot magnifies and pushes its neighbors apart. */
public final class HotbarDock {
    public static final HotbarDock INSTANCE = new HotbarDock();
    private static final Identifier SELECTION = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar_selection");
    private static final Identifier HOTBAR_FRAME = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar");
    private static final Identifier OFFHAND_LEFT = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar_offhand_left");
    private static final Identifier OFFHAND_RIGHT = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar_offhand_right");
    private static final Identifier ATTACK_BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar_attack_indicator_background");
    private static final Identifier ATTACK_PROGRESS = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar_attack_indicator_progress");
    private static final int SLOT = 20, OFFHAND = 9;
    private final float[] scale = new float[10];
    private final float[] velocity = new float[10];
    private float dockCenter;
    private float centerVelocity;
    private long last = System.nanoTime();
    private int heldSlot = -1;
    private net.minecraft.world.item.Item heldItem;
    private double heldTime;
    private float slideY;
    private float slideVelocity;
    private static float barLift;

    private boolean active;
    private Player lastPlayer;
    public void reset() {
        heldSlot = -1; heldItem = null; heldTime = 0; slideY = slideVelocity = 0;
        java.util.Arrays.fill(scale, 1); java.util.Arrays.fill(velocity, 0);
        centerVelocity = 0; last = System.nanoTime(); barLift = 0;
    }

    public boolean render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft client = Minecraft.getInstance();
        DockConfig c = DynamicDockClient.config;
        if (!c.enabled || !(client.getCameraEntity() instanceof Player player)) {
            active = false; barLift = 0; lastPlayer = null; return false;
        }
        int selected = player.getInventory().getSelectedSlot();
        if (lastPlayer != player) { reset(); dockCenter = selected; lastPlayer = player; }
        active = true;
        float frame = (float)Math.clamp((System.nanoTime() - last) / 1e9, 0, .05);
        last = System.nanoTime();
        centerVelocity += (selected - dockCenter) * 90 * frame;
        centerVelocity *= (float)Math.exp(-14 * frame);
        dockCenter = Math.clamp(dockCenter + centerVelocity * frame, 0, 8);
        boolean left = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        for (int i = 0; i < 10; i++) {
            float distance = Math.abs((i < 9 ? i : left ? -1 : 9) - dockCenter);
            float target = c.magnification == 0 ? 1 : 1 + c.magnification * DockLayout.curve(distance, c.falloff) + (i == selected ? .15f : 0);
            velocity[i] += (target - scale[i]) * 100 * frame;
            velocity[i] *= (float)Math.exp(-15 * frame);
            scale[i] = c.magnification == 0 ? 1 : Math.clamp(scale[i] + velocity[i] * frame, 1, 3.65f);
        }
        ItemStack held = player.getInventory().getSelectedItem();
        if (selected != heldSlot || held.getItem() != heldItem || held.isEmpty() || client.gui.screen() != null || client.options.keyAttack.isDown() || client.options.keyUse.isDown()) {
            heldTime = 0; heldSlot = selected; heldItem = held.getItem();
        } else heldTime += frame;
        float hideTarget = c.autoHide && !held.isEmpty() && heldTime >= c.hideDelay ? 1 : 0;
        slideVelocity += (hideTarget - slideY) * 80 * frame;
        slideVelocity *= (float)Math.exp(-14 * frame);
        slideY = Math.clamp(slideY + slideVelocity * frame, 0, 1);
        boolean indicator = client.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && player.getAttackStrengthScale(0) < 1;
        DockLayout layout = new DockLayout(scale, dockCenter, !player.getOffhandItem().isEmpty(), left, indicator,
                c.scale, g.guiWidth(), g.guiHeight() - 45, c.frames, c.shelf, c.shadow ? c.shadowStrength : 0, maxPop(player, dt));
        float slide = slideY * ((layout.bottom - layout.top) * layout.size + 12);
        barLift = Math.max(0, (layout.bottom - layout.top) * layout.size - 21 - slide);
        g.pose().pushMatrix();
        g.pose().translate(g.guiWidth() / 2f, g.guiHeight() - 2 - layout.bottom * layout.size + slide);
        g.pose().scale(layout.size);
        drawDock(g, client, dt, player, player.getInventory(), player.getOffhandItem(), left, selected, layout, indicator);
        g.pose().popMatrix();
        return true;
    }

    private static float maxPop(Player player, DeltaTracker dt) {
        float max = Math.max(0, player.getOffhandItem().getPopTime() - dt.getGameTimeDeltaPartialTick(false));
        for (int i = 0; i < 9; i++) max = Math.max(max, player.getInventory().getItem(i).getPopTime() - dt.getGameTimeDeltaPartialTick(false));
        return max;
    }
    private void drawDock(GuiGraphicsExtractor g, Minecraft client, DeltaTracker dt, Player player, Inventory inventory,
                          ItemStack offhand, boolean left, int selected, DockLayout layout, boolean indicator) {
        if (DynamicDockClient.config.shelf) {
            g.nextStratum();
            GlassSurface.draw(g, Math.round(layout.left), -22, Math.round(layout.right - layout.left), 22);
            g.nextStratum();
        }
        HumanoidArm arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        for (int i = 0; i < 9; i++) if (i != selected) drawSlot(g, client, dt, player, inventory, offhand, arm, i, -19, 0, selected, layout.x);
        drawSlot(g, client, dt, player, inventory, offhand, arm, OFFHAND, -19, 0, selected, layout.x);
        drawSlot(g, client, dt, player, inventory, offhand, arm, selected, -19, 0, selected, layout.x);
        if (indicator) {
            // Vanilla places this on the main-hand side, opposite the offhand.
            int x = Math.round(left ? layout.right + 6 : layout.left - 24);
            float strength = player.getAttackStrengthScale(0);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ATTACK_BACKGROUND, x, -20, 18, 18);
            int progress = Math.clamp((int)(strength * 19), 0, 18);
            if (progress > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, ATTACK_PROGRESS, 18, 18, 0, 18 - progress, x, -2 - progress, 18, progress);
        }
    }
    private static final HotbarDock PREVIEW = new HotbarDock();
    public static void preview(GuiGraphicsExtractor g, int bottom, float availableHeight) {
        Minecraft client = Minecraft.getInstance();
        DockConfig c = DynamicDockClient.config;
        float center = (float)((Math.sin(System.nanoTime() / 1e9 * .65) + 1) * 4);
        int selected = Math.clamp(Math.round(center), 0, 8);
        Player player = client.player;
        boolean left = player == null || player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        for (int i = 0; i < 10; i++) PREVIEW.scale[i] = c.magnification == 0 ? 1 :
                1 + c.magnification * DockLayout.curve(Math.abs((i < 9 ? i : left ? -1 : 9) - center), c.falloff) + (i == selected ? .15f : 0);
        ItemStack offhand = player == null ? ItemStack.EMPTY : player.getOffhandItem();
        DockLayout layout = new DockLayout(PREVIEW.scale, center, !offhand.isEmpty(), left, false,
                c.scale, g.guiWidth(), availableHeight, c.frames, c.shelf, c.shadow ? c.shadowStrength : 0, player == null ? 0 : maxPop(player, client.getDeltaTracker()));
        g.pose().pushMatrix();
        g.pose().translate(g.guiWidth() / 2f, bottom - layout.bottom * layout.size);
        g.pose().scale(layout.size);
        PREVIEW.drawDock(g, client, client.getDeltaTracker(), player, player == null ? null : player.getInventory(), offhand, left, selected, layout, false);
        g.pose().popMatrix();
    }
    public static float barLift() { return barLift; }
    public static boolean shifting() { return DynamicDockClient.config.enabled && INSTANCE.active && Minecraft.getInstance().getCameraEntity() instanceof Player; }

    private void drawSlot(GuiGraphicsExtractor g, Minecraft client, DeltaTracker dt, Player player, Inventory inventory,
                          ItemStack offhand, HumanoidArm arm, int i, int itemY, int height, int selected, float[] pos) {
        float s = scale[i];
        if (i < 9) {
            applyScale(g, pos[i], height - 3, s);
            if (i == selected && DynamicDockClient.config.frames) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_FRAME, 182, 22, 1 + i * SLOT, 0,
                        Math.round(pos[i] - 10), height - 22, 20, 22);
                int frameX = Math.round(pos[i] - 12);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, frameX, height - 23, 24, 23);
                // Vanilla's 24x23 selection sprite ends at the screen edge, without a bottom border.
                // Close the floating frame with its top border, preserving resource-pack colors.
                var sprite = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI).getSprite(SELECTION);
                g.blit(sprite.atlasLocation(), frameX, height, frameX + 24, height + 1,
                        sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV(1f / 23));
            }
            if (inventory == null) {
                // 26.3 item components are bound only after joining a world. Use neutral slot numbers in title-menu previews.
                g.text(client.font, Integer.toString(i + 1), Math.round(pos[i] - 3), itemY + 4, 0xFFD0D0D0, false);
            } else drawItem(g, client, dt, inventory.getItem(i), Math.round(pos[i] - 8), itemY, i + 1, player);
            g.pose().popMatrix();
        } else if (!offhand.isEmpty()) {
            float spriteCenter = pos[i] + (arm == HumanoidArm.LEFT ? 3.5f : -3.5f);
            applyScale(g, spriteCenter, height, s);
            if (DynamicDockClient.config.frames && arm == HumanoidArm.LEFT) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND_LEFT, Math.round(spriteCenter - 14.5f), height - 24, 29, 24);
            } else if (DynamicDockClient.config.frames) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND_RIGHT, Math.round(spriteCenter - 14.5f), height - 24, 29, 24);
            }
            g.pose().popMatrix();
            applyScale(g, pos[i], height - 3, s);
            drawItem(g, client, dt, offhand, Math.round(pos[i] - 8), itemY, 10, player);
            g.pose().popMatrix();
        }
    }

    private static void applyScale(GuiGraphicsExtractor g, float cx, float cy, float s) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s);
        g.pose().translate(-cx, -cy);
    }

    private static void drawItem(GuiGraphicsExtractor g, Minecraft client, DeltaTracker dt, ItemStack stack,
                                 int x, int y, int slotIndex, Player player) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - dt.getGameTimeDeltaPartialTick(false);
        if (pop > 0) {
            float s = 1 + pop / 5f;
            g.pose().pushMatrix();
            g.pose().translate(x + 8, y + 12);
            g.pose().scale(1f / s, (s + 1) / 2f);
            g.pose().translate(-(x + 8), -(y + 12));
        }
        if (player == null) g.item(stack, x, y, slotIndex);
        else g.item(player, stack, x, y, slotIndex);
        if (pop > 0) g.pose().popMatrix();
        g.itemDecorations(client.font, stack, x, y);
    }

    private HotbarDock() {
        java.util.Arrays.fill(scale, 1);
    }
}
