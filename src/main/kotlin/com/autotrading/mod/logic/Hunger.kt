package com.autotrading.mod.logic

enum class Hunger {
    WORK,
    EAT,
    STOP,
}

const val HUNGER_FULL = 20

private const val POINTS_PER_ICON = 2

/** Fewer than three hunger icons. Each icon is two food points. */
fun shouldEat(foodLevel: Int): Boolean = foodLevel < 3 * POINTS_PER_ICON

/** One hunger icon or less. Further work can starve the player. */
fun mustStopHungry(foodLevel: Int): Boolean = foodLevel <= POINTS_PER_ICON

/**
 * Below three icons, eat until the bar is full when the inventory has food.
 * With no food, keep working until a single icon is left, then stand.
 */
fun hungerAction(foodLevel: Int, hasFood: Boolean, mealStarted: Boolean): Hunger {
    if (foodLevel >= HUNGER_FULL) return Hunger.WORK
    if ((mealStarted || shouldEat(foodLevel)) && hasFood) return Hunger.EAT
    if (mustStopHungry(foodLevel)) return Hunger.STOP
    return Hunger.WORK
}
