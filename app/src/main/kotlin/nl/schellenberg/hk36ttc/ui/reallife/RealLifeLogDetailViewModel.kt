package nl.schellenberg.hk36ttc.ui.reallife

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData
import nl.schellenberg.hk36ttc.core.metar.MetarParseError
import nl.schellenberg.hk36ttc.core.metar.MetarParseResult
import nl.schellenberg.hk36ttc.core.metar.MetarParser
import nl.schellenberg.hk36ttc.core.metar.PressureAltitude
import nl.schellenberg.hk36ttc.core.metar.WindComponents
import nl.schellenberg.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.schellenberg.hk36ttc.core.perf.PerformanceNormalData
import nl.schellenberg.hk36ttc.core.perf.PerformanceCalculator
import nl.schellenberg.hk36ttc.core.perf.TakeoffResult
import nl.schellenberg.hk36ttc.core.reallife.ReallifeDetectionConfigData
import nl.schellenberg.hk36ttc.core.reallife.TakeoffDetectionResult
import nl.schellenberg.hk36ttc.core.reallife.TakeoffDetector
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.AirfieldEntity
import nl.schellenberg.hk36ttc.data.local.ConditionsSource
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerEntity
import nl.schellenberg.hk36ttc.data.metar.HistoricalMetarRepository
import nl.schellenberg.hk36ttc.data.metar.HistoricalMetarResult
import nl.schellenberg.hk36ttc.data.metar.MetarRepository
import nl.schellenberg.hk36ttc.data.metar.MetarRepository.Companion.stationCode
import nl.schellenberg.hk36ttc.ui.perf.TakeoffSurfaceType

/** Which of the three ways to fill in [ConditionsFormState] the pilot currently has selected in
 * the edit form -- distinct from [ConditionsSource], which is what gets SAVED once a fetch (or
 * manual typing) has actually happened. */
enum class ConditionsSourceMode { LIVE, HISTORICAL, MANUAL }

/** Editable draft of a [RealLifeLogEntity]'s Fase 4c fields. Every numeric field is a `String` so
 * a half-typed value doesn't get silently coerced or rejected while the pilot is still typing --
 * parsed only in [RealLifeLogDetailViewModel.saveConditions]. */
data class ConditionsFormState(
    val sourceMode: ConditionsSourceMode = ConditionsSourceMode.MANUAL,
    val surfaceType: TakeoffSurfaceType = TakeoffSurfaceType.ASFALT,
    val slopePct: String = "0",
    val airfieldId: Long? = null,
    val manualStationIcao: String = "",
    val oatC: String = "",
    val pressureAltM: String = "",
    val windDirectionDeg: String = "",
    val windSpeedKts: String = "",
    val metarRaw: String? = null,
    val metarObservedAtEpochMs: Long? = null,
    val source: ConditionsSource = ConditionsSource.MANUAL,
    val fetchInProgress: Boolean = false,
    val fetchError: String? = null
)

data class RealLifeLogDetailState(
    val log: RealLifeLogEntity? = null,
    val registration: String? = null,
    val locationCount: Int = 0,
    val imuCount: Int = 0,
    val barometerCount: Int = 0,
    val markers: List<RealLifeMarkerEntity> = emptyList(),
    val detection: TakeoffDetectionResult? = null,
    val comparison: TakeoffResult? = null,
    /** True when [comparison] was computed with the headwind component excluded because real
     * wind was recorded but no detected roll heading was available to resolve it against --
     * distinct from "no wind was ever entered", which needs no warning. See [computeComparison]. */
    val comparisonHeadwindExcluded: Boolean = false,
    val airfields: List<AirfieldEntity> = emptyList(),
    val isEditingConditions: Boolean = false,
    val form: ConditionsFormState = ConditionsFormState()
)

/**
 * Fase 4c: owns everything [RealLifeLogDetailScreen] shows, including the read-only summary that
 * used to live directly in that composable's `LaunchedEffect` -- an interactive edit-conditions
 * form (surface/slope, live/historical/manual weather fetch) doesn't fit the old stateless
 * pattern, so this consolidates ALL of the screen's state into one place rather than leaving two
 * loading patterns side by side in one file.
 */
class RealLifeLogDetailViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val logId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val performanceCorrections: PerformanceCorrectionsData,
    private val metarRepository: MetarRepository,
    private val historicalMetarRepository: HistoricalMetarRepository,
    private val metarConfig: MetarConfigData,
    private val reallifeDetectionConfig: ReallifeDetectionConfigData
) : ViewModel() {

    private val _state = MutableStateFlow(RealLifeLogDetailState())
    val state: StateFlow<RealLifeLogDetailState> = _state

    init {
        viewModelScope.launch { loadAll() }
    }

    private suspend fun loadAll() {
        val log = repository.getRealLifeLog(logId)
        val registration = repository.getById(profileId)?.registration
        val locationEntities = repository.getLocationSamples(logId)
        val barometerEntities = repository.getBarometerSamples(logId)
        val imuCount = repository.countImuSamples(logId)
        val markers = repository.getRealLifeMarkers(logId)
        val detection = if (log != null) {
            TakeoffDetector.detect(
                locationEntities.map { it.toDetectionSample() },
                barometerEntities.map { it.toDetectionSample() },
                barometerAvailable = log.barometerAvailable,
                thresholds = reallifeDetectionConfig.toThresholds()
            )
        } else {
            null
        }
        // observeAirfields() is a Flow; a one-shot read is enough here (the list rarely changes
        // mid-session, and the picker reopens this screen's edit form fresh each time).
        val airfieldList = repository.observeAirfields().first()
        val comparisonResult = log?.let { l -> computeComparison(l, detection) }

        _state.update {
            it.copy(
                log = log,
                registration = registration,
                locationCount = locationEntities.size,
                imuCount = imuCount,
                barometerCount = barometerEntities.size,
                markers = markers,
                detection = detection,
                comparison = comparisonResult?.result,
                comparisonHeadwindExcluded = comparisonResult?.headwindExcluded ?: false,
                airfields = airfieldList
            )
        }
    }

    // --- Edit-form entry/exit ---

    fun beginEditingConditions() {
        val log = _state.value.log ?: return
        _state.update {
            it.copy(
                isEditingConditions = true,
                form = ConditionsFormState(
                    sourceMode = when (log.conditionsSource?.let { s -> runCatching { ConditionsSource.valueOf(s) }.getOrNull() }) {
                        ConditionsSource.METAR_LIVE -> ConditionsSourceMode.LIVE
                        ConditionsSource.METAR_HISTORICAL -> ConditionsSourceMode.HISTORICAL
                        else -> ConditionsSourceMode.MANUAL
                    },
                    surfaceType = log.surfaceType?.let { s -> runCatching { TakeoffSurfaceType.valueOf(s) }.getOrNull() } ?: TakeoffSurfaceType.ASFALT,
                    slopePct = log.slopePct?.toString() ?: "0",
                    airfieldId = log.airfieldId,
                    oatC = log.oatC?.toString() ?: "",
                    pressureAltM = log.pressureAltM?.toString() ?: "",
                    windDirectionDeg = log.windDirectionDeg?.toString() ?: "",
                    windSpeedKts = log.windSpeedKts?.toString() ?: "",
                    metarRaw = log.metarRaw,
                    metarObservedAtEpochMs = log.metarObservedAtEpochMs,
                    source = log.conditionsSource?.let { s -> runCatching { ConditionsSource.valueOf(s) }.getOrNull() } ?: ConditionsSource.MANUAL
                )
            )
        }
    }

    fun cancelEditingConditions() {
        _state.update { it.copy(isEditingConditions = false) }
    }

    // --- Field setters ---

    /** Switching modes invalidates whatever the OLD mode last fetched -- without clearing
     * [ConditionsFormState.source]/[ConditionsFormState.metarRaw]/[ConditionsFormState.metarObservedAtEpochMs]
     * here, a Live fetch followed by switching to Manual and typing different values would save
     * `conditionsSource = METAR_LIVE` alongside a `metarRaw` that no longer matches what was
     * actually used. Numeric fields (OAT/wind/etc.) are deliberately left as-is -- they are a
     * reasonable starting point to hand-edit in Manual mode, and a genuine re-fetch after
     * switching to Live/Historical overwrites them (and re-sets these three) via
     * [applyParsedMetar] regardless. */
    fun updateSourceMode(mode: ConditionsSourceMode) = updateForm {
        it.copy(sourceMode = mode, fetchError = null, source = ConditionsSource.MANUAL, metarRaw = null, metarObservedAtEpochMs = null)
    }
    fun updateSurfaceType(type: TakeoffSurfaceType) = updateForm { it.copy(surfaceType = type) }
    fun updateSlopePct(value: String) = updateForm { it.copy(slopePct = value) }
    fun selectAirfield(airfieldId: Long?) = updateForm { it.copy(airfieldId = airfieldId) }
    fun updateManualStation(value: String) = updateForm { it.copy(manualStationIcao = value) }
    fun updateOatC(value: String) = updateForm { it.copy(oatC = value) }
    fun updatePressureAltM(value: String) = updateForm { it.copy(pressureAltM = value) }
    fun updateWindDirectionDeg(value: String) = updateForm { it.copy(windDirectionDeg = value) }
    fun updateWindSpeedKts(value: String) = updateForm { it.copy(windSpeedKts = value) }

    private fun updateForm(block: (ConditionsFormState) -> ConditionsFormState) {
        _state.update { it.copy(form = block(it.form)) }
    }

    // --- Fetch actions ---

    /** Fetches the CURRENT METAR (reusing the same [MetarRepository] the calc screens use) for a
     * recording that just happened. Requires an airfield to be selected in the form. Returns the
     * launched [Job] -- [MetarRepository] hops onto a real `Dispatchers.IO`, not the virtual test
     * dispatcher, so a test awaits completion via `job.join()` rather than
     * `testScheduler.advanceUntilIdle()`, which only controls the virtual scheduler. Callers in
     * production (a Compose `onClick`) simply ignore the return value. */
    fun fetchLive(): Job {
        val airfieldId = _state.value.form.airfieldId ?: run {
            updateForm { it.copy(fetchError = "Kies eerst een vliegveld.") }
            return Job().apply { complete() }
        }
        return viewModelScope.launch {
            updateForm { it.copy(fetchInProgress = true, fetchError = null) }
            val airfield = repository.getAirfield(airfieldId)
            if (airfield == null) {
                updateForm { it.copy(fetchInProgress = false, fetchError = "Vliegveld niet gevonden.") }
                return@launch
            }
            metarRepository.refreshOne(airfield, metarConfig)
            val refreshed = repository.getAirfield(airfieldId)
            val raw = refreshed?.metarRaw
            if (raw == null) {
                updateForm { it.copy(fetchInProgress = false, fetchError = "Geen METAR beschikbaar voor dit station.") }
                return@launch
            }
            applyParsedMetar(raw, observedAtEpochMs = System.currentTimeMillis(), source = ConditionsSource.METAR_LIVE, elevationM = refreshed.elevationM)
        }
    }

    /** Fetches the historical METAR nearest [RealLifeLogEntity.startedAtEpochMs] -- the path that
     * backfills the 9 already-recorded logs. Station comes from the selected airfield, or from a
     * manually typed ICAO code when no airfield applies. Returns the launched [Job] -- see
     * [fetchLive]'s KDoc for why. */
    fun fetchHistorical(): Job {
        val log = _state.value.log ?: return Job().apply { complete() }
        val form = _state.value.form
        return viewModelScope.launch {
            updateForm { it.copy(fetchInProgress = true, fetchError = null) }
            val selectedAirfield = form.airfieldId?.let { repository.getAirfield(it) }
            val station = selectedAirfield?.stationCode() ?: form.manualStationIcao.trim().uppercase().ifBlank { null }
            if (station == null) {
                updateForm { it.copy(fetchInProgress = false, fetchError = "Kies een vliegveld met station, of typ een ICAO-stationcode.") }
                return@launch
            }
            when (val result = historicalMetarRepository.fetchNearest(station, log.startedAtEpochMs, metarConfig)) {
                is HistoricalMetarResult.Success ->
                    applyParsedMetar(result.rawMetar, result.observedAtEpochMs, ConditionsSource.METAR_HISTORICAL, selectedAirfield?.elevationM)
                HistoricalMetarResult.NotFound ->
                    updateForm { it.copy(fetchInProgress = false, fetchError = "Geen historische METAR gevonden voor deze datum.") }
                HistoricalMetarResult.Disabled ->
                    updateForm { it.copy(fetchInProgress = false, fetchError = "Historische opzoeking is uitgeschakeld.") }
                is HistoricalMetarResult.Failed ->
                    updateForm { it.copy(fetchInProgress = false, fetchError = result.reason) }
            }
        }
    }

    private fun applyParsedMetar(raw: String, observedAtEpochMs: Long, source: ConditionsSource, elevationM: Double?) {
        when (val parsed = MetarParser.parse(raw)) {
            is MetarParseResult.Success -> {
                val metar = parsed.metar
                // Captured into locals before the null checks: a nullable property declared in
                // another module (ParsedMetar lives in :core) can't be smart-cast across module
                // boundaries, even though it's a plain val -- the compiler can't rule out a
                // custom getter with side effects in a different compilation unit.
                val qnhHpa = metar.qnhHpa
                val pressureAltM = if (elevationM != null && qnhHpa != null) {
                    PressureAltitude.fromElevationAndQnh(elevationM, qnhHpa).coerceAtLeast(0.0).toInt().toString()
                } else {
                    _state.value.form.pressureAltM
                }
                val windDirectionDeg = metar.windDirectionDeg
                val windDir = if (!metar.windVariableDirection && windDirectionDeg != null) {
                    windDirectionDeg.toInt().toString()
                } else {
                    _state.value.form.windDirectionDeg
                }
                updateForm {
                    it.copy(
                        fetchInProgress = false,
                        fetchError = null,
                        oatC = metar.temperatureC.toInt().toString(),
                        pressureAltM = pressureAltM,
                        windDirectionDeg = windDir,
                        windSpeedKts = metar.windSpeedKts.toInt().toString(),
                        metarRaw = raw,
                        metarObservedAtEpochMs = observedAtEpochMs,
                        source = source
                    )
                }
            }
            is MetarParseResult.Failure ->
                updateForm { it.copy(fetchInProgress = false, fetchError = "METAR kon niet worden gelezen: ${metarParseErrorMessage(parsed.reason)}") }
        }
    }

    private fun metarParseErrorMessage(reason: MetarParseError): String = when (reason) {
        MetarParseError.Empty -> "lege METAR-tekst"
        MetarParseError.MissingStation -> "geen stationcode gevonden"
        MetarParseError.MissingObservationTime -> "geen waarnemingstijd gevonden"
        MetarParseError.MissingWind -> "geen windgroep gevonden"
        MetarParseError.MissingTemperature -> "geen temperatuurgroep gevonden"
    }

    // --- Save ---

    fun saveConditions() {
        val log = _state.value.log ?: return
        val form = _state.value.form
        viewModelScope.launch {
            val updated = log.copy(
                airfieldId = form.airfieldId,
                surfaceType = form.surfaceType.name,
                // No `?: 0.0` fallback -- a blank field means "unknown", same as every sibling
                // field below, not a deliberately-entered flat runway.
                slopePct = form.slopePct.toDoubleOrNull(),
                oatC = form.oatC.toIntOrNull(),
                pressureAltM = form.pressureAltM.toIntOrNull(),
                windDirectionDeg = form.windDirectionDeg.toIntOrNull(),
                windSpeedKts = form.windSpeedKts.toIntOrNull(),
                metarRaw = form.metarRaw,
                metarObservedAtEpochMs = form.metarObservedAtEpochMs,
                conditionsSource = form.source.name
            )
            repository.updateRealLifeLog(updated)
            _state.update { it.copy(isEditingConditions = false) }
            loadAll()
        }
    }

    // --- Handboek-vs-gemeten ---

    /** Duplicated from [nl.schellenberg.hk36ttc.ui.perf.TakeoffViewModel.surfaceFactorFor] on
     * purpose -- this codebase already has this exact small mapping three times over (Takeoff/
     * Landing/Sleepvlucht), each with its own baseline nuance, and none of them share a common
     * helper. A fourth private copy matches the established pattern rather than introducing the
     * first shared abstraction for something that has historically diverged per screen. */
    private fun surfaceFactorFor(type: TakeoffSurfaceType): Double = when (type) {
        TakeoffSurfaceType.ASFALT -> 1.0
        TakeoffSurfaceType.DROOG_GRAS -> 1.0 + performanceNormal.takeoff.grassRunwayPenaltyMinPct / 100.0
        TakeoffSurfaceType.NAT_GRAS -> performanceCorrections.grassTakeoffFactors.wetGrassFactor
        TakeoffSurfaceType.ZACHTE_GROND -> performanceCorrections.grassTakeoffFactors.softGroundFactor
    }

    private data class ComparisonResult(val result: TakeoffResult, val headwindExcluded: Boolean)

    /** `marginFactor = 1.0` deliberately -- comparing against the raw AFM figure (`s2M`), not
     * `s2WithMarginM`: the margin is a pilot-chosen planning pad on top of the physical
     * prediction, not part of the prediction itself (see `PerformanceCalculator`'s own doc
     * comment and `performance_normal.json`'s "No safety margin included" note). */
    private fun computeComparison(log: RealLifeLogEntity, detection: TakeoffDetectionResult?): ComparisonResult? {
        val surfaceTypeName = log.surfaceType ?: return null
        val oatC = log.oatC ?: return null
        val pressureAltM = log.pressureAltM ?: return null
        val slopePct = log.slopePct ?: return null
        val surfaceType = runCatching { TakeoffSurfaceType.valueOf(surfaceTypeName) }.getOrNull() ?: return null

        val heading = detection?.rollHeadingDegTrue
        val windDir = log.windDirectionDeg?.toDouble()
        val windSpeed = log.windSpeedKts?.toDouble()
        val realWindRecorded = windDir != null && windSpeed != null
        // Direct null checks (not `realWindRecorded`) so the compiler can smart-cast windDir/
        // windSpeed to non-null below.
        val headwindKts = if (heading != null && windDir != null && windSpeed != null) {
            WindComponents.compute(windDir, windSpeed, heading).headwindKts
        } else {
            0.0
        }
        // Only flag the genuine "we have real wind but can't apply it" case -- not "no wind was
        // ever entered", which needs no warning since there was never anything to lose.
        val headwindExcluded = realWindRecorded && heading == null

        val result = PerformanceCalculator.calculateTakeoff(
            performanceNormal, performanceCorrections,
            oatC = oatC.toDouble(), pressureAltM = pressureAltM.toDouble(), headwindKts = headwindKts,
            surfaceFactor = surfaceFactorFor(surfaceType), slopePct = slopePct, marginFactor = 1.0
        )
        return ComparisonResult(result, headwindExcluded)
    }

    companion object {
        fun factory(
            repository: AircraftProfileRepository,
            profileId: Long,
            logId: Long,
            performanceNormal: PerformanceNormalData,
            performanceCorrections: PerformanceCorrectionsData,
            metarRepository: MetarRepository,
            historicalMetarRepository: HistoricalMetarRepository,
            metarConfig: MetarConfigData,
            reallifeDetectionConfig: ReallifeDetectionConfigData
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return RealLifeLogDetailViewModel(
                    repository, profileId, logId, performanceNormal, performanceCorrections,
                    metarRepository, historicalMetarRepository, metarConfig, reallifeDetectionConfig
                ) as T
            }
        }
    }
}
