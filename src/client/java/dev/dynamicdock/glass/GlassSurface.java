package dev.dynamicdock.glass;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.dynamicdock.DynamicDockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

public final class GlassSurface {
    public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var c = DynamicDockClient.config;
        if (w <= 0 || h <= 0 || c.opacity <= 0) return;
        // The exact same quantized radius drives the material, tint and fallback geometry.
        int corner = Math.clamp(Math.round(c.cornerRadius / 11 * 255), 0, 255);
        float radius = Math.min(w, h) * .5f * corner / 255;
        float shadow = c.shadow ? c.shadowStrength : 0;
        boolean glass = !(Minecraft.getInstance().gui.overlay() instanceof LoadingOverlay) && GlassBackdrop.request();
        if (glass) {
            int data = Math.round(c.opacity * 255) << 24 | corner << 16
                    | Math.round(c.refraction / 2 * 255) << 8 | Math.round((c.edges ? c.edgeIntensity : 0) / 2 * 255);
            var pose = new Matrix3x2f(g.pose());
            ScreenRectangle scissor = g.scissorStack.peek();
            int padding = Math.max(1, (int)Math.ceil(6 * shadow + 1));
            ScreenRectangle bounds = new ScreenRectangle(x - padding, y - padding, w + padding * 2, h + padding * 2).transformMaxBounds(pose);
            if (scissor != null) bounds = bounds.intersection(scissor);
            if (bounds != null) g.guiRenderState.addGuiElement(new Surface(pose, x, y, w, h, data, shadow, padding, scissor, bounds));
        } else {
            if (shadow > 0) for (int spread = (int)Math.ceil(6 * shadow); spread >= 1; spread--) {
                round(g, x - spread, y - spread + shadow, w + 2 * spread, h + 2 * spread,
                        radius + spread, alpha(0xFF000000, .025f * shadow * c.opacity));
            }
            round(g, x, y, w, h, radius, alpha(0xFF263D54, c.opacity * .6f));
            if (c.edges && c.edgeIntensity > 0) {
                // Two nested silhouettes make a matching rounded rim without a rectangular outline.
                round(g, x, y, w, h, radius, alpha(0xFFE8EFF7, .12f * c.edgeIntensity * c.opacity));
                round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), alpha(0xFF263D54, .32f * c.opacity));
            }
        }
        if (c.tintIntensity > 0) {
            g.nextStratum();
            round(g, x, y, w, h, radius, alpha(0xFF000000 | c.tintColor, c.tintIntensity * c.opacity * .65f));
            g.nextStratum();
        }
    }
    private record Surface(Matrix3x2f pose, int x, int y, int w, int h, int data, float shadow, int padding,
                           ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return GlassPipelines.SURFACE; }
        @Override public TextureSetup textureSetup() { return GlassBackdrop.textures(); }
        @Override public void buildVertices(VertexConsumer v) {
            float p = padding;
            vertex(v, x - p, y - p, -p / w, -p / h);
            vertex(v, x - p, y + h + p, -p / w, 1 + p / h);
            vertex(v, x + w + p, y + h + p, 1 + p / w, 1 + p / h);
            vertex(v, x + w + p, y - p, 1 + p / w, -p / h);
        }
        private void vertex(VertexConsumer v, float px, float py, float u, float t) {
            // Position.z carries a per-surface parameter; the vertex shader restores GUI depth to zero.
            v.addVertex(pose.m00() * px + pose.m10() * py + pose.m20(),
                    pose.m01() * px + pose.m11() * py + pose.m21(), shadow).setUv(u, t).setColor(data);
        }
    }
    public static int alpha(int color, float opacity) { return Math.round((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24 | color & 0xFFFFFF; }
    /** Coverage at the transformed framebuffer resolution; no double radius scaling. */
    private static void round(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
        int resolution = Math.max(1, (int)Math.ceil(Minecraft.getInstance().getWindow().getGuiScale() * Math.hypot(g.pose().m10(), g.pose().m11())));
        int rows = Math.max(1, Math.round(h * resolution));
        double r = Math.min(radius, Math.min(w, h) / 2) * resolution;
        g.pose().pushMatrix();
        g.pose().translate(x, y); g.pose().scale(1f / resolution);
        int width = Math.round(w * resolution);
        for (int row = 0; row < rows; row++) {
            double dy = Math.max(0, Math.max(r - row - .5, row + .5 - (rows - r)));
            double inset = r - Math.sqrt(Math.max(0, r * r - dy * dy));
            int whole = (int)Math.ceil(inset);
            g.fill(whole, row, width - whole, row + 1, color);
            float coverage = (float)(whole - inset);
            if (coverage > 0) {
                g.fill(whole - 1, row, whole, row + 1, alpha(color, coverage));
                g.fill(width - whole, row, width - whole + 1, row + 1, alpha(color, coverage));
            }
        }
        g.pose().popMatrix();
    }
    private GlassSurface() { }
}
