package com.autotrading.mod.logic

enum class OfferFit {
    MATCH,
    LOCKED,
    DIFFERENT,
}

data class OfferView(
    val costA: String,
    val costACount: Int,
    val costB: String,
    val costBCount: Int,
    val result: String,
    val resultCount: Int,
    val outOfStock: Boolean,
)

data class Recipe(
    val costA: String,
    val costACount: Int,
    val costB: String,
    val costBCount: Int,
    val result: String,
    val resultCount: Int,
)

fun fit(offer: OfferView, recipe: Recipe): OfferFit {
    if (!sameCost(offer.costA, offer.costACount, recipe.costA, recipe.costACount)) return OfferFit.DIFFERENT
    if (!sameCost(offer.result, offer.resultCount, recipe.result, recipe.resultCount)) return OfferFit.DIFFERENT
    if (!sameOptional(offer.costB, offer.costBCount, recipe.costB, recipe.costBCount)) return OfferFit.DIFFERENT
    return if (offer.outOfStock) OfferFit.LOCKED else OfferFit.MATCH
}

fun canPay(haveA: Int, haveB: Int, recipe: Recipe): Boolean {
    if (haveA < recipe.costACount) return false
    return recipe.costB.isBlank() || recipe.costBCount <= 0 || haveB >= recipe.costBCount
}

/** How many result items still fit into empty slots and partial stacks. */
fun roomFor(emptySlots: Int, partialRoom: Int, count: Int): Boolean {
    if (count <= 0) return true
    return partialRoom + emptySlots * 64 >= count
}

private fun sameCost(item: String, count: Int, wantItem: String, wantCount: Int): Boolean =
    item == wantItem && count == wantCount

private fun sameOptional(item: String, count: Int, wantItem: String, wantCount: Int): Boolean {
    val present = item.isNotBlank() && count > 0
    val wanted = wantItem.isNotBlank() && wantCount > 0
    if (present != wanted) return false
    return !wanted || (item == wantItem && count == wantCount)
}
