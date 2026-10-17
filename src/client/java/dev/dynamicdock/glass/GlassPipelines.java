package dev.dynamicdock.glass;

import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Optional pipelines: unsupported/missing shaders leave the vanilla-geometry fallback usable. */
public final class GlassPipelines {
    public static final RenderPipeline SURFACE = RenderPipelines.registerOptional(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(id("pipeline/glass"))
                    .withVertexShader(id("core/glass_button"))
                    .withFragmentShader(id("core/glass_button"))
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1).withCull(false).build());
    private static final RenderPipeline[] X = new RenderPipeline[9], Y = new RenderPipeline[9];
    static {
        for (int i = 1; i <= 8; i++) {
            X[i] = blur("blur_x_" + i, true, i * .25f);
            Y[i] = blur("blur_y_" + i, false, i * .25f);
        }
    }
    private static RenderPipeline blur(String name, boolean horizontal, float strength) {
        return RenderPipelines.registerOptional(RenderPipeline.builder()
                .withLocation(id("pipeline/" + name)).withVertexShader("core/screenquad")
                .withFragmentShader(id("core/glass_blur"))
                .withShaderDefine(horizontal ? "HORIZONTAL" : "VERTICAL")
                .withShaderDefine("BLUR_SCALE", strength)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).build());
    }
    public static RenderPipeline horizontal(int step) { return X[step]; }
    public static RenderPipeline vertical(int step) { return Y[step]; }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("dynamicdock", name); }
    public static void initialize() { }
    private GlassPipelines() { }
}
