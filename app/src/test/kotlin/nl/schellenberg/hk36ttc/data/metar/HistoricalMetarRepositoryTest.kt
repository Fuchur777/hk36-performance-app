package nl.schellenberg.hk36ttc.data.metar

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData

/** [csvResponse] is the literal shape confirmed against the real IEM endpoint (station EHRD,
 * one specific date) while designing Fase 4c — see HistoricalMetarRepository's KDoc. */
class HistoricalMetarRepositoryTest {

    private val config = MetarConfigData.DEFAULT

    private val csvResponse = """
        station,valid,metar
        EHRD,2026-08-22 08:55,EHRD 220855Z 24012KT 9999 FEW025 18/12 Q1017 NOSIG
        EHRD,2026-08-22 09:55,EHRD 220955Z 25014KT 9999 SCT028 19/12 Q1017 NOSIG
        EHRD,2026-08-22 10:55,EHRD 221055Z 25013KT 9999 SCT030 20/11 Q1016 NOSIG
        EHRD,2026-08-22 11:55,M
    """.trimIndent()

    @Test
    fun `returns the observation nearest the requested instant, not the first one`() = runTest {
        val repository = HistoricalMetarRepository(fetch = { csvResponse })
        // 2026-08-22 10:10 UTC -- closer to the 09:55 report (15 min) than the 10:55 one (45 min).
        val atEpochMs = java.time.LocalDateTime.parse("2026-08-22T10:10:00")
            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

        val result = repository.fetchNearest("EHRD", atEpochMs, config)

        assertTrue(result is HistoricalMetarResult.Success)
        assertTrue(result.rawMetar.contains("25014KT"), "expected the 09:55 report")
    }

    @Test
    fun `a missing observation row is skipped, not returned as an empty METAR`() = runTest {
        val onlyMissing = """
            station,valid,metar
            EHRD,2026-08-22 11:55,M
        """.trimIndent()
        val repository = HistoricalMetarRepository(fetch = { onlyMissing })

        val result = repository.fetchNearest("EHRD", 0L, config)

        assertEquals(HistoricalMetarResult.NotFound, result)
    }

    @Test
    fun `a network failure is reported, not thrown`() = runTest {
        val repository = HistoricalMetarRepository(fetch = { throw IOException("no connection") })

        val result = repository.fetchNearest("EHRD", 0L, config)

        assertTrue(result is HistoricalMetarResult.Failed)
    }

    @Test
    fun `blanking the historical endpoint in config switches the lookup off entirely`() = runTest {
        val repository = HistoricalMetarRepository(fetch = { error("must not be called") })

        val result = repository.fetchNearest("EHRD", 0L, config.copy(historicalFetchUrlTemplate = ""))

        assertEquals(HistoricalMetarResult.Disabled, result)
    }

    @Test
    fun `parseCsv skips the header row and unparseable or missing rows`() {
        val parsed = HistoricalMetarRepository.parseCsv(csvResponse)
        assertEquals(3, parsed.size, "the M row must be skipped")
        assertTrue(parsed.all { it.second.startsWith("EHRD") })
    }
}
