package dev.dynamicdock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.dynamicdock.glass.GlassBackdrop;
import dev.dynamicdock.glass.GlassPipelines;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GlassGuiRendererMixin {
    @Shadow @Final private List<?> draws;
    @Inject(method = "render", at = @At("HEAD"))
    private void dynamicdock$frame(CallbackInfo ci) { GlassBackdrop.beginFrame(); }
    @WrapMethod(method = "executeDrawRange")
    private void dynamicdock$capture(Supplier<String> label, RenderTarget target, GpuBufferSlice transforms,
                                     int start, int end, Operation<Void> original) {
        if (GlassBackdrop.skipDraws()) {
            // A failed copy/blur must never sample uninitialized textures. The next extraction uses fallback geometry.
            int segment = start;
            for (int i = start; i < end; i++) if (((GuiDrawAccessor)draws.get(i)).dynamicdock$pipeline() == GlassPipelines.SURFACE) {
                if (segment < i) original.call(label, target, transforms, segment, i);
                segment = i + 1;
            }
            if (segment < end) original.call(label, target, transforms, segment, end);
            return;
        }
        if (GlassBackdrop.needsCapture()) for (int i = start; i < end; i++) {
            if (((GuiDrawAccessor)draws.get(i)).dynamicdock$pipeline() == GlassPipelines.SURFACE) {
                if (i > start) original.call(label, target, transforms, start, i);
                if (GlassBackdrop.capture(target)) original.call(label, target, transforms, i, end);
                else dynamicdock$capture(label, target, transforms, i, end, original);
                return;
            }
        }
        original.call(label, target, transforms, start, end);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicdock$close(CallbackInfo ci) { GlassBackdrop.close(); }
}
