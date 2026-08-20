package nl.schellenberg.hk36ttc.ui.profile

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.schellenberg.hk36ttc.core.wb.parseWbConstants
import nl.schellenberg.hk36ttc.data.local.FakeAircraftProfileDao
import nl.schellenberg.hk36ttc.data.local.fakeAircraftProfileRepository

/**
 * Regression coverage for the reported bug: tapping "Opslaan" with a blank registration used to
 * do nothing at all — no error, no feedback — because `errors` was a stored map only refreshed
 * inside `update()`, so a fresh screen's `save()` saw a stale empty map and silently no-opped.
 * Same fix, same shape, as [nl.schellenberg.hk36ttc.ui.airfield.AirfieldEditViewModelTest].
 *
 * See TakeoffViewModelTest's KDoc for why `Dispatchers.setMain`/`resetMain` need the opt-in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {

    /** Literal copy of `app/src/main/assets/data/weight_balance_constants.json` — `core`'s own
     * test fixture for this file isn't visible to `app`'s test source set. Only `mtow_kg`
     * actually matters here (it seeds the new-profile MTOW default); the rest is filled in
     * purely so the JSON parses. */
    private val wbConstantsJson = """
        {
          "mtow_kg": 770,
          "max_non_lifting_parts_mass_kg": 610,
          "min_useful_load_on_seats_kg": 55,
          "max_useful_load_per_seat_kg": 110,
          "max_baggage_kg": 12,
          "fuel_density_kg_per_l": 0.75,
          "fuel_tank_capacity_l": { "standard_55l": 55, "long_range_79l": 79 },
          "arms_mm_aft_of_datum": {
            "seat_payload": 143, "fuel_tank_standard_55l": 727, "fuel_tank_long_range_79l": 824
          },
          "trim_weights_kg": { "table": [ { "deficit_kg": 5, "trim_mass_kg": 1.7 } ] }
        }
    """.trimIndent()

    private val wbConstants = parseWbConstants(wbConstantsJson)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(dao: FakeAircraftProfileDao = FakeAircraftProfileDao(), profileId: Long = 0L) =
        ProfileEditViewModel(fakeAircraftProfileRepository(profileDao = dao), profileId, wbConstants)

    @Test
    fun `saving a brand-new profile untouched shows an error instead of doing nothing`() = runTest {
        val vm = viewModel()

        assertEquals(emptyMap(), vm.state.value.errors, "no error before the pilot has tried anything")

        vm.save()

        assertEquals(true, vm.state.value.errors.containsKey("registration"))
        assertEquals(false, vm.state.value.isSaved, "nothing should have been persisted")
    }

    /** The other half of the bug: nudging an unrelated stepper used to reveal the registration
     * error early, even though the pilot hadn't touched or even reached that field yet. */
    @Test
    fun `touching an unrelated field before ever attempting to save shows no error`() = runTest {
        val vm = viewModel()

        vm.update { it.copy(mtowKg = 750) }

        assertEquals(emptyMap(), vm.state.value.errors)
    }

    @Test
    fun `typing a registration after a failed save clears the error`() = runTest {
        val vm = viewModel()
        vm.save()
        assertEquals(true, vm.state.value.errors.containsKey("registration"))

        vm.update { it.copy(registration = "PH-1600") }

        assertEquals(false, vm.state.value.errors.containsKey("registration"))
    }

    @Test
    fun `saving with a registration persists and never sets the error`() = runTest {
        val dao = FakeAircraftProfileDao()
        val vm = viewModel(dao)

        vm.update { it.copy(registration = "PH-1600") }
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyMap(), vm.state.value.errors)
        assertEquals(true, vm.state.value.isSaved)
        assertEquals("PH-1600", dao.getById(vm.state.value.id)?.registration)
    }

    /** The CG business-rule error is deliberately NOT gated on a save attempt — unlike a blank
     * required field, it can only ever fire from a value the pilot just set (the defaults are
     * always internally consistent), so live feedback while adjusting the steppers is useful
     * rather than premature. */
    @Test
    fun `the aft-limit business rule error shows live, without needing a save attempt first`() = runTest {
        val vm = viewModel()

        vm.update { it.copy(cgEnvelopeForwardLimitMm = 420, cgEnvelopeAftLimitMm = 400) }

        assertEquals(true, vm.state.value.errors.containsKey("cgEnvelopeAftLimitMm"))
        vm.save()
        assertEquals(false, vm.state.value.isSaved, "an invalid CG envelope must never save")
    }
}
