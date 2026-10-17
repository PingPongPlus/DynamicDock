package dev.dynamicdock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dynamicdock.HotbarDock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class DockHudMixin {
    @Unique private boolean dynamicdock$barsPushed;
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void dynamicdock$hotbar(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        if (HotbarDock.INSTANCE.render(g, dt)) ci.cancel();
    }
    @Inject(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void dynamicdock$liftBars(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        if (HotbarDock.shifting() && HotbarDock.barLift() > 0) {
            dynamicdock$barsPushed = true;
            g.pose().pushMatrix(); g.pose().translate(0, -HotbarDock.barLift());
        }
    }
    @WrapMethod(method = "extractHotbarAndDecorations")
    private void dynamicdock$restorePose(GuiGraphicsExtractor g, DeltaTracker dt, Operation<Void> original) {
        dynamicdock$barsPushed = false;
        try { original.call(g, dt); }
        finally { if (dynamicdock$barsPushed) { g.pose().popMatrix(); dynamicdock$barsPushed = false; } }
    }
}
