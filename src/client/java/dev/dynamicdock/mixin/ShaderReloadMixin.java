package dev.dynamicdock.mixin;
import dev.dynamicdock.glass.GlassBackdrop;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ShaderManager.class)
public abstract class ShaderReloadMixin {
    // apply runs on the render thread, after shader preparation and before the cache swap.
    @Inject(method = "apply", at = @At("HEAD"))
    private void dynamicdock$reload(CallbackInfo ci) { GlassBackdrop.reload(); }
}
