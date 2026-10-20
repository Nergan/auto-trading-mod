package com.autotrading.mod.mixin

import com.autotrading.mod.client.Panel
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.input.MouseButtonInfo
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(MouseHandler::class)
abstract class MouseMixin {
    @Inject(
        method = ["onButton"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
        )],
        cancellable = true,
    )
    private fun autotradingClick(window: Long, info: MouseButtonInfo, action: Int, callback: CallbackInfo) {
        val minecraft = Minecraft.getInstance()
        val x = minecraft.mouseHandler.getScaledXPos(minecraft.window)
        val y = minecraft.mouseHandler.getScaledYPos(minecraft.window)
        if (Panel.click(x, y, info.button())) callback.cancel()
    }
}
