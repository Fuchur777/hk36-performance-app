package nl.glcillustrious.hk36ttc.data.metar

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FakeAirfieldDao
import nl.glcillustrious.hk36ttc.data.local.fakeAircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository.Companion.shouldAutoRefresh
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository.Companion.stationCode

class MetarRepositoryTest {

    private val config = MetarConfigData.DEFAULT

    private val response = """
        METAR EHDL 170655Z AUTO 26005KT 5000 15/14 Q1015 WHT
        METAR EHGR 170655Z AUTO VRB02KT 4000 15/15 Q1017 GRN
    """.trimIndent()

    private fun airfield(
        id: Long,
        name: String,
        icao: String?,
        station: String? = null,
        metarRaw: String? = null,
        enteredAt: Long? = null
    ) = AirfieldEntity(
        id = id,
        name = name,
        icao = icao,
        metarStationIcao = station,
        elevationM = 15.0,
        metarRaw = metarRaw,
        metarEnteredAtEpochMs = enteredAt
    )

    @Test
    fun `fetched reports are stored on the matching airfield, keyed by station not order`() = runTest {
        val dao = FakeAirfieldDao()
        dao.seed(airfield(1, "Gilze-Rijen", "EHGR"))
        dao.seed(airfield(2, "Deelen", "EHDL"))
        val repository = fakeAircraftProfileRepository(airfieldDao = dao)
        val metar = MetarRepository(repository, fetch = { response })

        val result = metar.refresh(listOf(dao.getById(1)!!, dao.getById(2)!!), config)

        assertEquals(MetarFetchResult.Success(updated = 2, withoutReport = 0), result)
        assertTrue(dao.getById(1)!!.metarRaw!!.contains("VRB02KT"), "EHGR must get its own report")
        assertTrue(dao.getById(2)!!.metarRaw!!.contains("26005KT"), "EHDL must get its own report")
    }

    /**
     * The offline-first guarantee: a network failure must leave whatever the pilot already had
     * completely intact, so they can still fly on the previous report.
     */
    @Test
    fun `a network failure leaves an already-stored METAR untouched`() = runTest {
        val existing = "EHGR 161825Z 35006KT 9999 21/14 Q1016"
        val dao = FakeAirfieldDao()
        dao.seed(airfield(1, "Gilze-Rijen", "EHGR", metarRaw = existing, enteredAt = 1_000L))
        val repository = fakeAircraftProfileRepository(airfieldDao = dao)
        val metar = MetarRepository(repository, fetch = { throw IOException("no connection") })

        val result = metar.refresh(listOf(dao.getById(1)!!), config)

        assertTrue(result is MetarFetchResult.Failed)
        assertEquals(existing, dao.getById(1)!!.metarRaw)
        assertEquals(1_000L, dao.getById(1)!!.metarEnteredAtEpochMs)
    }

    @Test
    fun `a station with no observation is reported, not overwritten with nothing`() = runTest {
        val existing = "EHTL 161825Z 09008KT 9999 15/10 Q1015"
        val dao = FakeAirfieldDao()
        // Terlet has no station of its own, so the service returns nothing for it.
        dao.seed(airfield(1, "Terlet", "EHTL", metarRaw = existing, enteredAt = 1_000L))
        val repository = fakeAircraftProfileRepository(airfieldDao = dao)
        val metar = MetarRepository(repository, fetch = { response })

        val result = metar.refresh(listOf(dao.getById(1)!!), config)

        assertEquals(MetarFetchResult.Success(updated = 0, withoutReport = 1), result)
        assertEquals(existing, dao.getById(1)!!.metarRaw)
    }

    /** An unchanged report must not reset the timestamp, or a stale observation looks fresh. */
    @Test
    fun `re-fetching an identical report does not refresh the timestamp`() = runTest {
        val unchanged = "METAR EHGR 170655Z AUTO VRB02KT 4000 15/15 Q1017 GRN"
        val dao = FakeAirfieldDao()
        dao.seed(airfield(1, "Gilze-Rijen", "EHGR", metarRaw = unchanged, enteredAt = 1_000L))
        val repository = fakeAircraftProfileRepository(airfieldDao = dao)
        val metar = MetarRepository(repository, fetch = { response })

        val result = metar.refresh(listOf(dao.getById(1)!!), config)

        assertEquals(MetarFetchResult.Success(updated = 0, withoutReport = 0), result)
        assertEquals(1_000L, dao.getById(1)!!.metarEnteredAtEpochMs)
    }

    @Test
    fun `blanking the endpoint in config switches the lookup off entirely`() = runTest {
        val dao = FakeAirfieldDao()
        dao.seed(airfield(1, "Gilze-Rijen", "EHGR"))
        val repository = fakeAircraftProfileRepository(airfieldDao = dao)
        val metar = MetarRepository(repository, fetch = { error("must not be called") })

        val result = metar.refresh(listOf(dao.getById(1)!!), config.copy(fetchUrlTemplate = ""))

        assertEquals(MetarFetchResult.Disabled, result)
    }

    @Test
    fun `the explicit METAR station wins over the field's own ICAO`() {
        // Terlet reads Deelen's weather — the whole reason these are two separate fields.
        assertEquals("EHDL", airfield(1, "Terlet", "EHTL", station = "EHDL").stationCode())
        assertEquals("EHGR", airfield(1, "Gilze-Rijen", "EHGR").stationCode())
        assertEquals(null, airfield(1, "Weiland", null).stationCode())
        assertEquals(null, airfield(1, "Kort", "EH").stationCode())
    }

    @Test
    fun `auto-refresh triggers on a missing or aged report, not on a fresh one`() {
        val now = 10_000_000L
        val ageLimitMs = config.autoRefreshAfterMinutes * 60_000L

        assertTrue(shouldAutoRefresh(airfield(1, "G", "EHGR"), config, now), "missing METAR")
        assertTrue(
            shouldAutoRefresh(
                airfield(1, "G", "EHGR", metarRaw = "x", enteredAt = now - ageLimitMs - 1),
                config, now
            ),
            "aged METAR"
        )
        assertTrue(
            !shouldAutoRefresh(
                airfield(1, "G", "EHGR", metarRaw = "x", enteredAt = now - 60_000L),
                config, now
            ),
            "one-minute-old METAR must not trigger a fetch"
        )
        assertTrue(
            !shouldAutoRefresh(airfield(1, "Weiland", null), config, now),
            "no station code means nothing to fetch"
        )
    }
}
