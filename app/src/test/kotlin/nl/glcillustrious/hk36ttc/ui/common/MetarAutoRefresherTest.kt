package nl.glcillustrious.hk36ttc.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity

/**
 * The underlying "is this METAR old enough" threshold is
 * [nl.glcillustrious.hk36ttc.data.metar.MetarRepositoryTest]'s job to cover. This file only
 * tests what [MetarAutoRefresher] itself adds on top: the once-per-airfield-per-instance
 * bookkeeping — the exact thing that was missing on [nl.glcillustrious.hk36ttc.ui.perf.LandingViewModel]
 * and [nl.glcillustrious.hk36ttc.ui.perf.SleepvluchtViewModel] before this class existed.
 */
class MetarAutoRefresherTest {

    private val config = MetarConfigData.DEFAULT

    private fun staleAirfield(id: Long) = AirfieldEntity(
        id = id,
        name = "Gilze-Rijen",
        icao = "EHGR",
        metarStationIcao = null,
        elevationM = 15.0,
        metarRaw = null,
        metarEnteredAtEpochMs = null
    )

    @Test
    fun `a stale airfield may auto-refresh exactly once`() {
        val refresher = MetarAutoRefresher()
        val airfield = staleAirfield(1)

        assertTrue(refresher.shouldAutoRefresh(airfield, config), "first attempt is allowed")
        assertFalse(refresher.shouldAutoRefresh(airfield, config), "a second attempt for the same airfield is not")
    }

    @Test
    fun `dedup is per airfield, not global`() {
        val refresher = MetarAutoRefresher()

        assertTrue(refresher.shouldAutoRefresh(staleAirfield(1), config))
        assertTrue(refresher.shouldAutoRefresh(staleAirfield(2), config), "a different airfield gets its own attempt")
    }

    @Test
    fun `null airfield never triggers a refresh`() {
        assertFalse(MetarAutoRefresher().shouldAutoRefresh(null, config))
    }

    @Test
    fun `an airfield that isn't due yet is never marked as attempted`() {
        val refresher = MetarAutoRefresher()
        val fresh = staleAirfield(1).copy(metarRaw = "x", metarEnteredAtEpochMs = System.currentTimeMillis())

        assertFalse(refresher.shouldAutoRefresh(fresh, config), "not stale, so no refresh yet")
        // Not having been "attempted" matters for a caller that later sees the same airfield
        // become genuinely stale within one instance's lifetime — it must still get its chance.
        val staleNow = fresh.copy(metarEnteredAtEpochMs = 0L)
        assertTrue(refresher.shouldAutoRefresh(staleNow, config), "now stale, and never attempted before")
    }
}
