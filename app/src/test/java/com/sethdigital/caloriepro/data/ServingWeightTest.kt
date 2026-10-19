package com.sethdigital.caloriepro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServingWeightTest {

    @Test
    fun `reads the weight from common serving sizes`() {
        assertEquals(ServingWeight(55.0, "g"), ServingWeight.parse("2/3 cup (55g)"))
        assertEquals(ServingWeight(30.0, "g"), ServingWeight.parse("30 g"))
        assertEquals(ServingWeight(100.0, "g"), ServingWeight.parse("100 g"))
        assertEquals(ServingWeight(28.0, "g"), ServingWeight.parse("2 cookies (28 grams)"))
        assertEquals(ServingWeight(42.5, "g"), ServingWeight.parse("1.5 oz (42,5 g)"))
    }

    @Test
    fun `keeps drinks in millilitres`() {
        assertEquals(ServingWeight(355.0, "ml"), ServingWeight.parse("1 can (355 mL)"))
        assertEquals(ServingWeight(240.0, "ml"), ServingWeight.parse("8 fl oz (240ml)"))
        assertEquals(ServingWeight(250.0, "ml"), ServingWeight.parse("250 millilitres"))
    }

    @Test
    fun `no weight in the serving size`() {
        assertNull(ServingWeight.parse(null))
        assertNull(ServingWeight.parse(""))
        assertNull(ServingWeight.parse("1 cup"))
        assertNull(ServingWeight.parse("1 large egg"))
        assertNull(ServingWeight.parse("0 g"))
    }

    @Test
    fun `converts between servings and weight`() {
        val weight = ServingWeight(55.0, "g")
        assertEquals(2.0, weight.servingsIn(110.0), 0.0001)
        assertEquals(82.5, weight.weightOf(1.5), 0.0001)
    }
}
