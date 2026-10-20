package com.autotrading.mod.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HungerTest {
    @Test
    fun threeIconsKeepWorking() {
        assertEquals(Hunger.WORK, hungerAction(6, true, false))
        assertEquals(Hunger.WORK, hungerAction(20, true, true))
    }

    @Test
    fun belowThreeIconsStartsAMealUntilFull() {
        assertEquals(Hunger.EAT, hungerAction(5, true, false))
        assertEquals(Hunger.EAT, hungerAction(12, true, true))
        assertEquals(Hunger.WORK, hungerAction(12, false, true))
    }

    @Test
    fun missingFoodKeepsWorkingUntilOneIcon() {
        assertEquals(Hunger.WORK, hungerAction(5, false, false))
        assertEquals(Hunger.WORK, hungerAction(4, false, false))
        assertEquals(Hunger.STOP, hungerAction(2, false, false))
        assertEquals(Hunger.STOP, hungerAction(0, false, false))
        assertEquals(Hunger.EAT, hungerAction(2, true, false))
    }
}