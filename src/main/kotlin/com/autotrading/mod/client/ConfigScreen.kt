package com.autotrading.mod.client

import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object ConfigScreen {
    fun create(parent: Screen): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("autotrading.configuration.title"))
            .setSavingRunnable {
                Settings.save()
                val minecraft = net.minecraft.client.Minecraft.getInstance()
                if (minecraft.player != null) Session.onEnabledChanged(minecraft)
            }
        val entries = builder.entryBuilder()
        val category = builder.getOrCreateCategory(Component.translatable("autotrading.configuration.title"))
        category.addEntry(
            entries.startBooleanToggle(Component.translatable("autotrading.configuration.show_panel"), Settings.showPanel)
                .setDefaultValue(true)
                .setSaveConsumer { Settings.showPanel = it }
                .build(),
        )
        category.addEntry(
            entries.startStrField(Component.translatable("autotrading.configuration.corner"), Settings.corner)
                .setDefaultValue("top-right")
                .setTooltip(Component.translatable("autotrading.configuration.corner.tooltip"))
                .setSaveConsumer { Settings.corner = it.trim().ifEmpty { "top-right" } }
                .build(),
        )
        category.addEntry(
            entries.startStrField(Component.translatable("autotrading.configuration.pickup"), Settings.pickup.joinToString(", "))
                .setDefaultValue("minecraft:melon")
                .setSaveConsumer { Settings.pickup.clear(); Settings.pickup.addAll(split(it)) }
                .build(),
        )
        category.addEntry(
            entries.startStrField(Component.translatable("autotrading.configuration.cost_a"), Settings.costA)
                .setDefaultValue("minecraft:melon")
                .setSaveConsumer { Settings.costA = it.trim() }
                .build(),
        )
        category.addEntry(
            entries.startIntField(Component.translatable("autotrading.configuration.cost_a_count"), Settings.costACount)
                .setDefaultValue(1)
                .setMin(1)
                .setMax(64)
                .setSaveConsumer { Settings.costACount = it }
                .build(),
        )
        category.addEntry(
            entries.startStrField(Component.translatable("autotrading.configuration.cost_b"), Settings.costB)
                .setDefaultValue("")
                .setSaveConsumer { Settings.costB = it.trim() }
                .build(),
        )
        category.addEntry(
            entries.startIntField(Component.translatable("autotrading.configuration.cost_b_count"), Settings.costBCount)
                .setDefaultValue(0)
                .setMin(0)
                .setMax(64)
                .setSaveConsumer { Settings.costBCount = it }
                .build(),
        )
        category.addEntry(
            entries.startStrField(Component.translatable("autotrading.configuration.result"), Settings.result)
                .setDefaultValue("minecraft:emerald")
                .setSaveConsumer { Settings.result = it.trim() }
                .build(),
        )
        category.addEntry(
            entries.startIntField(Component.translatable("autotrading.configuration.result_count"), Settings.resultCount)
                .setDefaultValue(1)
                .setMin(1)
                .setMax(64)
                .setSaveConsumer { Settings.resultCount = it }
                .build(),
        )
        category.addEntry(
            entries.startStrField(Component.translatable("autotrading.configuration.deposit"), Settings.deposit.joinToString(", "))
                .setDefaultValue("minecraft:emerald")
                .setTooltip(Component.translatable("autotrading.configuration.list.tooltip"))
                .setSaveConsumer { Settings.deposit.clear(); Settings.deposit.addAll(split(it)) }
                .build(),
        )
        category.addEntry(
            entries.startBooleanToggle(Component.translatable("autotrading.configuration.enabled"), Settings.enabled)
                .setDefaultValue(false)
                .setSaveConsumer { Settings.enabled = it }
                .build(),
        )
        return builder.build()
    }

    private fun split(text: String) = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
