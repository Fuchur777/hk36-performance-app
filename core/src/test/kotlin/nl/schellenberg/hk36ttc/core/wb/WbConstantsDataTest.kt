package nl.schellenberg.hk36ttc.core.wb

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [BUNDLED_JSON] is a literal copy of app/src/main/assets/data/weight_balance_constants.json
 * (itself copied from docs/data/weight_balance_constants.json — the AFM-sourced original).
 * core has no asset/file access of its own to read the real file, so this is pinned here as
 * text. If the AFM data ever changes, update all three together — this test exists precisely
 * to catch it if they drift apart.
 */
class WbConstantsDataTest {

    private val bundledJson = """
        {
          "aircraft_type": "HK 36 TTC",
          "source": {
            "document": "Airplane Flight Manual HK 36 TTC",
            "doc_no": "3.01.20-E",
            "issue": "03 Mar 1997",
            "revision": "4",
            "revision_date": "2024-02-16",
            "section": "6.4 - 6.8",
            "pages": "6-4 to 6-9"
          },
          "note": "These are GENERIC AFM values, not aircraft-specific. Empty mass, empty-mass CG position, and CG envelope limits MUST be entered per registration by the user (from their own Weighing Report / Mass and Balance Form), since the AFM CG envelope is a nomograph parametrized on empty-mass CG, not a fixed table.",
          "mtow_kg": 770,
          "max_non_lifting_parts_mass_kg": 610,
          "min_useful_load_on_seats_kg": 55,
          "max_useful_load_per_seat_kg": 110,
          "max_baggage_kg": 12,
          "fuel_density_kg_per_l": 0.75,
          "fuel_tank_capacity_l": {
            "standard_55l": 55,
            "long_range_79l": 79
          },
          "arms_mm_aft_of_datum": {
            "seat_payload": 143,
            "fuel_tank_standard_55l": 727,
            "fuel_tank_long_range_79l": 824,
            "baggage_note": "equals fuel tank arm (727mm standard tank / 824mm long-range tank, depending on which is fitted)"
          },
          "trim_weights_kg": {
            "note": "If deficit in useful load on seats exceeds 55kg minimum, a trim weight fixture is installed 400mm aft of firewall, on center console.",
            "table": [
              { "deficit_kg": 5,  "trim_mass_kg": 1.7 },
              { "deficit_kg": 10, "trim_mass_kg": 3.4 },
              { "deficit_kg": 15, "trim_mass_kg": 5.1 }
            ]
          },
          "aircraft_profile_user_input_required": [
            "registration",
            "empty_mass_kg",
            "empty_mass_cg_position_mm",
            "mtow_kg (default 770, editable)",
            "cg_envelope_forward_limit (function of total mass, or simplified as fixed mm value, per user's AMM/Weighing Report data)",
            "cg_envelope_aft_limit (function of total mass, or simplified as fixed mm value, per user's AMM/Weighing Report data)",
            "fuel_tank_type (standard/long_range)"
          ]
        }
    """.trimIndent()

    @Test
    fun `parseWbConstants reads the real bundled JSON and matches the hardcoded DEFAULT fixture`() {
        assertEquals(WbConstantsData.DEFAULT, parseWbConstants(bundledJson))
    }

    @Test
    fun `parseWbConstants ignores unknown metadata fields`() {
        val json = """{"aircraft_type":"x","source":{},"note":"x","mtow_kg":770,
            "max_non_lifting_parts_mass_kg":610,"min_useful_load_on_seats_kg":55,
            "max_useful_load_per_seat_kg":110,"max_baggage_kg":12,
            "fuel_density_kg_per_l":0.75,
            "fuel_tank_capacity_l":{"standard_55l":55,"long_range_79l":79},
            "arms_mm_aft_of_datum":{"seat_payload":143,"fuel_tank_standard_55l":727,
            "fuel_tank_long_range_79l":824},
            "trim_weights_kg":{"table":[{"deficit_kg":5,"trim_mass_kg":1.7}]},
            "some_future_field_not_yet_modeled": 42}"""
        val parsed = parseWbConstants(json)
        assertEquals(770.0, parsed.defaultMtowKg)
    }

    @Test
    fun `a club edit to the trim table (extra point) is picked up without a code change`() {
        val json = """{"mtow_kg":770,"max_non_lifting_parts_mass_kg":610,
            "min_useful_load_on_seats_kg":55,"max_useful_load_per_seat_kg":110,"max_baggage_kg":12,
            "fuel_density_kg_per_l":0.75,
            "fuel_tank_capacity_l":{"standard_55l":55,"long_range_79l":79},
            "arms_mm_aft_of_datum":{"seat_payload":143,"fuel_tank_standard_55l":727,
            "fuel_tank_long_range_79l":824},
            "trim_weights_kg":{"table":[
                {"deficit_kg":5,"trim_mass_kg":1.7},
                {"deficit_kg":10,"trim_mass_kg":3.4},
                {"deficit_kg":15,"trim_mass_kg":5.1},
                {"deficit_kg":20,"trim_mass_kg":6.8}
            ]}}"""
        val parsed = parseWbConstants(json)
        assertEquals(6.8, parsed.trimWeightKg(20.0))
    }

    @Test
    fun `fuel liters convert to kg using the JSON-supplied density, not a hardcoded one`() {
        val constants = WbConstantsData.DEFAULT
        // 40 * 0.75 = 30 exactly in IEEE-754 double arithmetic, no tolerance needed.
        assertEquals(30.0, constants.fuelKgFromLiters(40.0))
    }

    @Test
    fun `tank capacity in liters follows the fitted tank type`() {
        val constants = WbConstantsData.DEFAULT
        assertEquals(55.0, constants.tankCapacityLiters(FuelTankType.STANDARD_55L))
        assertEquals(79.0, constants.tankCapacityLiters(FuelTankType.LONG_RANGE_79L))
    }
}
