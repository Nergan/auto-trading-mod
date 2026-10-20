package com.autotrading.mod.logic

enum class Step {
    FETCH,
    SELL,
    STORE,
    WAIT,
}

/** One of the 36 inventory slots stays empty so a trade still has somewhere to land. */
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

data class Pile(val item: String, val count: Int, val limit: Int)

data class Price(val item: String, val count: Int)

/**
 * True when the whole result fits and every unsold payment item can come back.
 * The trade screen fills each payment slot up to a full stack, not just the price.
 * Closing the screen puts that remainder back, and whatever does not fit is dropped.
 * [slots] are the 36 inventory slots in inventory order: hotbar, then the main grid.
 */
fun saleFits(slots: List<Pile>, prices: List<Price>, resultItem: String, resultCount: Int, resultLimit: Int): Boolean {
    if (resultCount <= 0) return true
    if (resultItem.isBlank() || resultLimit <= 0) return false
    val piles = merchantOrder(slots).map { it.copy() }.toMutableList()
    val leftovers = ArrayList<Pile>()
    for (price in prices) {
        if (price.item.isBlank() || price.count <= 0) continue
        var moved = 0
        var capUsed = 64
        for (index in piles.indices) {
            val pile = piles[index]
            if (pile.count <= 0 || pile.item != price.item) continue
            val cap = pile.limit.coerceAtLeast(1)
            if (moved >= cap) break
            capUsed = cap
            val take = minOf(cap - moved, pile.count)
            piles[index] = pile.copy(count = pile.count - take)
            moved += take
            if (moved >= cap) break
        }
        if (moved < price.count) return false
        val left = moved - price.count
        if (left > 0) leftovers += Pile(price.item, left, capUsed)
    }
    return keepsSale(piles, leftovers, emptyList(), resultItem, resultCount, resultLimit)
}

/**
 * Same check once the payment items are already sitting in the trade slots.
 * [slots] are what is still in the inventory. [staged] is what those slots hold.
 */
fun keepsSale(
    slots: List<Pile>,
    staged: List<Pile>,
    prices: List<Price>,
    resultItem: String,
    resultCount: Int,
    resultLimit: Int,
): Boolean {
    if (resultCount <= 0) return true
    if (resultItem.isBlank() || resultLimit <= 0) return false
    val piles = slots.map { it.copy() }.toMutableList()
    if (!place(piles, resultItem, resultCount, resultLimit)) return false
    val owed = prices.map { it.item to it.count }.toMutableList()
    for (stage in staged) {
        if (stage.count <= 0 || stage.item.isBlank()) continue
        var left = stage.count
        val index = owed.indexOfFirst { it.first == stage.item && it.second > 0 }
        if (index >= 0) {
            val (item, count) = owed[index]
            val used = minOf(count, left)
            owed[index] = item to (count - used)
            left -= used
        }
        if (left > 0 && !place(piles, stage.item, left, stage.limit.coerceAtLeast(1))) return false
    }
    return true
}

private fun merchantOrder(slots: List<Pile>): List<Pile> {
    if (slots.size != 36) return slots
    return slots.subList(9, 36) + slots.subList(0, 9)
}

private fun place(piles: MutableList<Pile>, item: String, count: Int, limit: Int): Boolean {
    var left = count
    val cap = limit.coerceAtLeast(1)
    for (index in piles.indices) {
        val pile = piles[index]
        if (pile.count <= 0 || pile.item != item) continue
        val room = (pile.limit.coerceAtLeast(1) - pile.count).coerceAtLeast(0)
        val add = minOf(room, left)
        if (add == 0) continue
        piles[index] = pile.copy(count = pile.count + add)
        left -= add
        if (left == 0) return true
    }
    for (index in piles.indices) {
        if (piles[index].count > 0) continue
        val add = minOf(cap, left)
        piles[index] = Pile(item, add, cap)
        left -= add
        if (left == 0) return true
    }
    return left == 0
}

/** What to do when a lap is not already walking the villagers. */
fun nextStep(emptySlots: Int, partialPickup: Boolean, payable: Boolean, resultFits: Boolean, deposit: Int): Step =
    when {
        shouldFetch(emptySlots, partialPickup) -> Step.FETCH
        payable && resultFits -> Step.SELL
        deposit > 0 -> Step.STORE
        else -> Step.WAIT
    }
