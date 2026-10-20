package com.autotrading.mod.mixin

import com.autotrading.mod.client.Panel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Screen::class)
abstract class ScreenMixin {
    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    private fun autotradingPanel(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo,
    ) {
        graphics.nextStratum()
        Panel.draw(graphics, Minecraft.getInstance().font)
    }
}
