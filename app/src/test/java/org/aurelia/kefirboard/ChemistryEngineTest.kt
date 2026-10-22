package org.aurelia.kefirboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemistryEngineTest {
    @Test fun hydrogenOxygen() {
        val r = ChemistryEngine.balance("H2 + O2 -> H2O")
        assertFalse(r.error)
        assertEquals("2H₂ + O₂ → 2H₂O", r.result)
    }

    @Test fun ironOxide() {
        val r = ChemistryEngine.balance("Fe + O2 = Fe2O3")
        assertFalse(r.error)
        assertEquals("4Fe + 3O₂ → 2Fe₂O₃", r.result)
    }

    @Test fun parenthesesAndHydroxide() {
        val r = ChemistryEngine.balance("Ca(OH)2 + HCl → CaCl2 + H2O")
        assertFalse(r.error)
        assertEquals("Ca(OH)₂ + 2HCl → CaCl₂ + 2H₂O", r.result)
    }

    @Test fun formatPreservesElementsAndSubscripts() {
        assertEquals("H₂SO₄", ChemistryEngine.prettyFormula("H2SO4"))
        assertEquals(2, ChemistryEngine.parseFormula("H₂O")["H"])
        assertEquals(2, ChemistryEngine.parseFormula("Ca(OH)2")["O"])
    }

    @Test fun invalidChemistryDoesNotInventResult() {
        assertTrue(ChemistryEngine.balance("H2 -> Fe").error)
        assertTrue(ChemistryEngine.balance("CO2").error)
    }

    @Test fun equationWithAlreadyPresentCoefficientsIsNormalized() {
        val r = ChemistryEngine.balance("2H2 + O2 → 2H2O")
        assertFalse(r.error)
        assertEquals("2H₂ + O₂ → 2H₂O", r.result)
    }
}
