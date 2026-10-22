package org.aurelia.kefirboard

import org.junit.Assert.assertEquals
import org.junit.Test

class MoreMathTests {
    @Test fun powerHasHigherPrecedenceThanMultiplication() {
        assertEquals("18", MathEngine.solve("2*3^2").answer)
    }

    @Test fun linearEquationWithNegativeConstant() {
        assertEquals("x = 6", MathEngine.solve("2x-12=0").answer)
    }

    @Test fun divisionByZeroIsNotPresentedAsAnAnswer() {
        assertEquals("Ошибка", MathEngine.solve("2/0").answer)
    }
}
