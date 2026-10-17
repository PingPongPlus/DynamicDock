package dev.dynamicdock.mixin;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer$Draw")
public interface GuiDrawAccessor {
    @Accessor("pipeline") RenderPipeline dynamicdock$pipeline();
}
