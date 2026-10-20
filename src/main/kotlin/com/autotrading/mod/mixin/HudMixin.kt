package com.autotrading.mod.mixin

import com.autotrading.mod.client.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Hud::class)
abstract class HudMixin {
    @Shadow
    private lateinit var minecraft: Minecraft

    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    private fun autotradingHud(graphics: GuiGraphicsExtractor, delta: DeltaTracker, callback: CallbackInfo) {
        if (minecraft.gui.screen() != null) return
        graphics.nextStratum()
        Panel.draw(graphics, minecraft.font)
    }
}
