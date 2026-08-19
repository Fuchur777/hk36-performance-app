package nl.glcillustrious.hk36ttc.ui.airfield

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogRepository
import nl.glcillustrious.hk36ttc.data.catalog.FakeAirportCatalogDao
import nl.glcillustrious.hk36ttc.data.catalog.FakeCatalogMetaDao
import nl.glcillustrious.hk36ttc.data.catalog.FakeRunwayCatalogDao
import nl.glcillustrious.hk36ttc.data.local.FakeAirfieldDao
import nl.glcillustrious.hk36ttc.data.local.fakeAircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository

/**
 * Regression coverage for the reported bug: tapping "Vliegveld opslaan" with a blank name did
 * nothing at all — no error, no feedback, just silence — which read as a broken button rather
 * than a validation problem. [AirfieldEditViewModel.saveAirfieldInfo] must surface that instead
 * of swallowing it.
 *
 * See TakeoffViewModelTest's KDoc for why `Dispatchers.setMain`/`resetMain` need the opt-in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AirfieldEditViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(airfieldDao: FakeAirfieldDao = FakeAirfieldDao(), airfieldId: Long = 0L): AirfieldEditViewModel {
        val repository = fakeAircraftProfileRepository(airfieldDao = airfieldDao)
        val catalog = AirportCatalogRepository(
            airportDao = FakeAirportCatalogDao(),
            runwayDao = FakeRunwayCatalogDao(),
            metaDao = FakeCatalogMetaDao(),
            userRepository = repository,
            openAsset = { error("no assets in this test") },
            transaction = { block -> block() },
            newTempFile = { error("no downloads in this test") }
        )
        val metarRepository = MetarRepository(repository, fetch = { error("no network in this test") })
        return AirfieldEditViewModel(repository, catalog, metarRepository, MetarConfigData.DEFAULT, airfieldId)
    }

    @Test
    fun `saving with a blank name shows an error instead of doing nothing`() = runTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(false, vm.state.value.nameError, "no error before the pilot has tried anything")

        vm.saveAirfieldInfo()
        testScheduler.advanceUntilIdle()

        assertEquals(true, vm.state.value.nameError)
        assertEquals(0L, vm.state.value.id, "nothing should have been persisted")
    }

    @Test
    fun `typing a name after a failed save clears the error`() = runTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.saveAirfieldInfo()
        testScheduler.advanceUntilIdle()
        assertEquals(true, vm.state.value.nameError)

        vm.update { it.copy(name = "Terlet") }

        assertEquals(false, vm.state.value.nameError)
    }

    @Test
    fun `saving with a real name persists and never sets the error`() = runTest {
        val airfieldDao = FakeAirfieldDao()
        val vm = viewModel(airfieldDao)
        testScheduler.advanceUntilIdle()

        vm.update { it.copy(name = "Terlet") }
        vm.saveAirfieldInfo()
        testScheduler.advanceUntilIdle()

        assertEquals(false, vm.state.value.nameError)
        assertEquals("Terlet", airfieldDao.getById(vm.state.value.id)?.name)
        assertNull(vm.metarFetchResult.value, "no network attempted for an airfield with no METAR/ICAO code")
    }
}
