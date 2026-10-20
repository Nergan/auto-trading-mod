package com.autotrading.mod.logic

enum class Step {
    FETCH,
    SELL,
    STORE,
    WAIT,
}

/** One empty slot stays free so the trade result has somewhere to land. */
fun shouldFetch(emptySlots: Int, partialPickup: Boolean): Boolean =
    emptySlots > 1 || (emptySlots == 1 && partialPickup)

/**
 * True when moving [incoming] items into the inventory still leaves one empty slot.
 * A stack that only tops up an existing pile does not spend a slot.
 */
fun keepsReserve(emptySlots: Int, mergeRoom: Int, incoming: Int, stackLimit: Int): Boolean {
    if (incoming <= 0 || stackLimit <= 0) return false
    val leftover = (incoming - mergeRoom.coerceAtLeast(0)).coerceAtLeast(0)
    if (leftover == 0) return true
    val slotsNeeded = (leftover + stackLimit - 1) / stackLimit
    return emptySlots - slotsNeeded >= 1
}

/** What to do when a lap is not already walking the villagers. */
fun nextStep(emptySlots: Int, partialPickup: Boolean, payable: Boolean, resultFits: Boolean, deposit: Int): Step =
    when {
        shouldFetch(emptySlots, partialPickup) -> Step.FETCH
        payable && resultFits -> Step.SELL
        deposit > 0 -> Step.STORE
        else -> Step.WAIT
    }
