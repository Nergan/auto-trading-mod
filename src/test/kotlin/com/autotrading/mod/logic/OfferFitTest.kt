package com.autotrading.mod.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfferFitTest {
    private val recipe = Recipe("minecraft:melon", 1, "", 0, "minecraft:emerald", 1)

    @Test
    fun discountedPriceMatches() {
        val offer = OfferView("minecraft:melon", 1, "", 0, "minecraft:emerald", 1, false)
        assertEquals(OfferFit.MATCH, fit(offer, recipe))
    }

    @Test
    fun basePriceWithoutDiscountDoesNotMatch() {
        val offer = OfferView("minecraft:melon", 2, "", 0, "minecraft:emerald", 1, false)
        assertEquals(OfferFit.DIFFERENT, fit(offer, recipe))
    }

    @Test
    fun matchingLockedOfferWaits() {
        val offer = OfferView("minecraft:melon", 1, "", 0, "minecraft:emerald", 1, true)
        assertEquals(OfferFit.LOCKED, fit(offer, recipe))
    }

    @Test
    fun secondCostMustBePresentOnBothSides() {
        val withExtra = recipe.copy(costB = "minecraft:stick", costBCount = 1)
        val missing = OfferView("minecraft:melon", 1, "", 0, "minecraft:emerald", 1, false)
        assertEquals(OfferFit.DIFFERENT, fit(missing, withExtra))
    }

    @Test
    fun paymentAndResultRoom() {
        assertEquals(true, canPay(1, 0, recipe))
        assertEquals(false, canPay(0, 0, recipe))
        assertEquals(true, roomFor(1, 0, 1))
        assertEquals(false, roomFor(0, 0, 1))
        assertEquals(true, roomFor(0, 1, 1))
    }
}
