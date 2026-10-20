package com.autotrading.mod.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

object Panel {
    private var applyBox = Box(0, 0, 0, 0)
    private var actionBox = Box(0, 0, 0, 0)
    private var statusBox = Box(0, 0, 0, 0)
    private var panelBox = Box(0, 0, 0, 0)

    fun draw(graphics: GuiGraphicsExtractor, font: Font) {
        val layout = layout(graphics.guiWidth(), graphics.guiHeight(), font)
        panelBox = layout.panel
        applyBox = layout.apply
        actionBox = layout.action
        statusBox = layout.status
        val notice = Alerts.text()
        if (!Settings.showPanel && notice == null) return
        if (notice != null && !Settings.showPanel) {
            val x = if (layout.right) graphics.guiWidth() - font.width(notice) - 4 else 4
            graphics.text(font, notice, x, 4, 0xFFFFFF55.toInt())
            return
        }
        graphics.fill(layout.panel.x, layout.panel.y, layout.panel.right(), layout.panel.bottom(), 0xC0101010.toInt())
        var line = layout.panel.y + 2
        line = text(graphics, font, layout, Session.statusText(), line, Session.statusColor())
        if (Session.canToggleFromStatus()) {
            graphics.fill(layout.status.x, layout.status.bottom() - 1, layout.status.right(), layout.status.bottom(), Session.statusColor())
        }
        Session.counterText()?.let { line = text(graphics, font, layout, it, line, 0xFFFFFFFF.toInt()) }
        Session.actionText()?.let { line = text(graphics, font, layout, it, line, 0xFFAAAAAA.toInt()) }
        notice?.let { text(graphics, font, layout, it, line, 0xFFFFFF55.toInt()) }
        button(graphics, font, layout.apply, "apply", layout.applyOn)
        button(graphics, font, layout.action, Session.actionLabel(), layout.actionOn)
    }

    fun click(x: Double, y: Double, button: Int): Boolean {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !Settings.showPanel) return false
        if (!panelBox.contains(x, y)) return false
        if (statusBox.contains(x, y) && Session.canToggleFromStatus()) {
            Session.toggleEnabled()
            return true
        }
        if (applyBox.contains(x, y) && Session.applyOn()) Session.apply()
        if (actionBox.contains(x, y) && Session.actionOn()) Session.toggleSetup()
        return true
    }

    private fun text(graphics: GuiGraphicsExtractor, font: Font, layout: Layout, value: String, line: Int, color: Int): Int {
        graphics.text(font, value, textX(layout, font.width(value)), line, color)
        return line + 10
    }

    private fun text(graphics: GuiGraphicsExtractor, font: Font, layout: Layout, value: Component, line: Int, color: Int): Int {
        graphics.text(font, value, textX(layout, font.width(value)), line, color)
        return line + 10
    }

    private fun textX(layout: Layout, textWidth: Int) =
        if (layout.right) layout.panel.right() - 3 - textWidth else layout.panel.x + 3

    private fun button(graphics: GuiGraphicsExtractor, font: Font, box: Box, label: String, on: Boolean) {
        graphics.fill(box.x, box.y, box.right(), box.bottom(), if (on) 0xFF2A2A2A.toInt() else 0xFF151515.toInt())
        graphics.text(font, label, box.x + 3, box.y + 1, if (on) 0xFFFFFFFF.toInt() else 0xFF777777.toInt())
    }

    private fun layout(screenWidth: Int, screenHeight: Int, font: Font): Layout {
        val status = Session.statusText()
        val statusWidth = font.width(status)
        val counter = Session.counterText()
        val actionLine = Session.actionText()
        val notice = Alerts.text()
        val applyW = font.width("apply") + 6
        val actionW = font.width(Session.actionLabel()) + 6
        val textWidth = maxOf(
            statusWidth,
            counter?.let { font.width(it) } ?: 0,
            actionLine?.let { font.width(it) } ?: 0,
            notice?.let { font.width(it) } ?: 0,
        )
        val panelWidth = maxOf(textWidth, applyW + 3 + actionW) + 6
        val lines = 1 + (if (counter != null) 1 else 0) + (if (actionLine != null) 1 else 0) + (if (notice != null) 1 else 0)
        val panelHeight = 2 + lines * 10 + 13
        val right = Settings.corner != "top-left" && Settings.corner != "bottom-left"
        val x = if (right) screenWidth - panelWidth - 4 else 4
        val bottom = Settings.corner == "bottom-left" || Settings.corner == "bottom-right"
        val y = if (bottom) screenHeight - panelHeight - 28 else 4
        val panel = Box(x, y, panelWidth, panelHeight)
        val statusX = if (right) panel.right() - 3 - statusWidth else panel.x + 3
        val statusBox = Box(statusX, panel.y + 2, statusWidth, 10)
        val buttonsY = panel.bottom() - 13
        val applyX = if (right) panel.right() - 3 - actionW - 3 - applyW else x + 3
        val actionX = if (right) panel.right() - 3 - actionW else applyX + applyW + 3
        return Layout(
            panel,
            statusBox,
            Box(applyX, buttonsY, applyW, 11),
            Box(actionX, buttonsY, actionW, 11),
            Session.applyOn(),
            Session.actionOn(),
            right,
        )
    }

    private data class Box(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun right() = x + w
        fun bottom() = y + h
        fun contains(px: Double, py: Double) = px >= x && px < right() && py >= y && py < bottom()
    }

    private data class Layout(
        val panel: Box,
        val status: Box,
        val apply: Box,
        val action: Box,
        val applyOn: Boolean,
        val actionOn: Boolean,
        val right: Boolean,
    )
}
