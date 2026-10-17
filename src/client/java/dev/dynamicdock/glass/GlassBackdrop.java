package dev.dynamicdock.glass;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.dynamicdock.DynamicDockClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import java.util.Optional;

/** Render-thread-owned scene capture, shared by every dock surface in a GUI frame. */
public final class GlassBackdrop {
    private static TextureTarget scene, blurX, blurred;
    private static boolean requested, captured, failed, resizeSkipped;
    private static int blurStep;

    public static boolean request() {
        blurStep = Math.clamp(Math.round(DynamicDockClient.config.blur * 4), 0, 8);
        if (failed || RenderSystem.getCompiledPipelineNullable(GlassPipelines.SURFACE) == null
                || blurStep > 0 && (RenderSystem.getCompiledPipelineNullable(GlassPipelines.horizontal(blurStep)) == null
                || RenderSystem.getCompiledPipelineNullable(GlassPipelines.vertical(blurStep)) == null)) return false;
        try {
            RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (scene == null || scene.width != target.width || scene.height != target.height
                    || scene.getColorTexture().getFormat() != target.getColorTexture().getFormat()) {
                close();
                scene = new TextureTarget("Dynamic Dock scene", target.width, target.height, target.getColorTexture().getFormat(), null);
                int w = Math.max(1, target.width / 2), h = Math.max(1, target.height / 2);
                blurX = new TextureTarget("Dynamic Dock blur X", w, h, target.getColorTexture().getFormat(), null);
                blurred = new TextureTarget("Dynamic Dock blur Y", w, h, target.getColorTexture().getFormat(), null);
            }
            requested = true;
            return true;
        } catch (RuntimeException e) { fail(e); return false; }
    }
    public static void beginFrame() {
        captured = resizeSkipped = false;
        if (!requested) close();
        requested = false;
    }
    public static boolean needsCapture() { return !captured && scene != null && !skipDraws(); }
    public static boolean skipDraws() { return failed || resizeSkipped; }
    public static boolean capture(RenderTarget target) {
        // The framebuffer can resize between extraction and drawing. Keep already prepared texture
        // views alive, skip this frame's glass, and let the next extraction allocate matching targets.
        if (scene.width != target.width || scene.height != target.height
                || scene.getColorTexture().getFormat() != target.getColorTexture().getFormat()) {
            resizeSkipped = true;
            return false;
        }
        try {
            // The caller has closed preceding GUI passes before this copy.
            scene.copyColorFrom(target);
            if (blurStep > 0) {
                blur(scene.getColorTextureView(), blurX, GlassPipelines.horizontal(blurStep));
                blur(blurX.getColorTextureView(), blurred, GlassPipelines.vertical(blurStep));
            }
            captured = true;
            return true;
        } catch (RuntimeException e) { fail(e); return false; }
    }
    private static void blur(GpuTextureView source, TextureTarget destination, RenderPipeline pipeline) {
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Dynamic Dock blur", destination.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("Sampler0", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
    public static TextureSetup textures() {
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        return TextureSetup.doubleTexture(scene.getColorTextureView(), sampler,
                blurStep == 0 ? scene.getColorTextureView() : blurred.getColorTextureView(), sampler);
    }
    private static void fail(RuntimeException e) {
        if (!failed) DynamicDockClient.LOGGER.warn("Glass unavailable; using rounded translucent dock until resource reload", e);
        failed = true;
    }
    public static boolean failed() { return failed; }
    public static void reload() { close(); failed = false; }
    public static void close() {
        if (scene != null) scene.destroyBuffers();
        if (blurX != null) blurX.destroyBuffers();
        if (blurred != null) blurred.destroyBuffers();
        scene = blurX = blurred = null;
        captured = requested = resizeSkipped = false;
    }
    private GlassBackdrop() { }
}
