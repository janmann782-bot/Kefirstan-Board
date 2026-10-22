package org.aurelia.kefirboard

import org.junit.Assert.assertEquals
import org.junit.Test

class MathEngineTest {
    @Test fun arithmetic() {
        assertEquals("14", MathEngine.solve("2+3*4").answer)
    }
    @Test fun solveEquation() {
        assertEquals("x = 4", MathEngine.solve("2x+4=12").answer)
    }
    @Test fun doNotPretendQuadraticsAreLinear() {
        assertEquals("Ошибка", MathEngine.solve("x^2=9").answer)
    }
}
