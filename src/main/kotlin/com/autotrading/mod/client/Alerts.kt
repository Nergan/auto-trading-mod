package com.autotrading.mod.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

object Alerts {
    var key = ""
    private var until = 0L
    private var lastSound = 0L
    private var lastKey = ""

    fun show(translationKey: String) {
        val now = System.currentTimeMillis()
        key = translationKey
        until = now + 7000
        if (translationKey != lastKey || now - lastSound > 8000) {
            lastKey = translationKey
            lastSound = now
            val player = Minecraft.getInstance().player ?: return
            player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.2f)
        }
    }

    fun lock() {
        if (System.currentTimeMillis() - lastSound < 2500 && lastKey == LOCK) return
        show(LOCK)
    }

    fun text(): Component? {
        if (key.isEmpty() || System.currentTimeMillis() > until) return null
        return Component.translatable(key)
    }

    private const val LOCK = "autotrading.notice.locked"
}
