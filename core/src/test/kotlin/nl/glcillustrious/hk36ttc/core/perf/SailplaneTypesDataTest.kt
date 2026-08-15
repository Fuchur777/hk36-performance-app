package nl.glcillustrious.hk36ttc.core.perf

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [excerptJson] is a literal excerpt of app/src/main/assets/data/sailplane_types.json (three
 * rows copied verbatim, including one non-round L/D value) — enough to catch a field-name or
 * parsing regression without duplicating all 192 entries here.
 */
class SailplaneTypesDataTest {

    private val excerptJson = """
        {
          "types": [
            { "name": "206 Hornet", "empty_mass_kg": 227, "mtow_kg": 418, "ld_ratio": 36.7 },
            { "name": "604 Kestrel", "empty_mass_kg": 455, "mtow_kg": 670, "ld_ratio": 43.6 },
            { "name": "SG-38", "empty_mass_kg": 150, "mtow_kg": 200, "ld_ratio": 8.3 }
          ]
        }
    """.trimIndent()

    @Test
    fun `parseSailplaneTypesData reads name, empty mass, MTOW and L-D for every entry`() {
        val data = parseSailplaneTypesData(excerptJson)
        assertEquals(3, data.types.size)

        val hornet = data.types[0]
        assertEquals("206 Hornet", hornet.name)
        assertEquals(227.0, hornet.emptyMassKg)
        assertEquals(418.0, hornet.mtowKg)
        assertEquals(36.7, hornet.ldRatio)

        val kestrel = data.types[1]
        assertEquals(455.0, kestrel.emptyMassKg)
        assertEquals(670.0, kestrel.mtowKg)

        val sg38 = data.types[2]
        assertEquals(8.3, sg38.ldRatio)
    }
}
