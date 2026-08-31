package nl.schellenberg.hk36ttc.core.reallife

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TEST-ONLY mirror of `:app`'s `data/export/RealLifeLogExport.kt` DTOs, field-for-field. `core`
 * cannot depend on `:app` (wrong dependency direction), so this is a deliberate duplicate rather
 * than a shared type. Deliberately lives in `src/test`, not `src/main` — unlike every other
 * `parseXData(json)` function in this module, production `:app` code never parses this JSON
 * shape (it maps Room entities to [LocationSample]/[BarometerSample] directly, no serialization
 * involved); only these tests, decoding the real recorded fixtures in
 * `docs/data/reallife-samples/`, need a parser at all. Do not "fix" this by moving it to `main`.
 *
 * Must tolerate real drift already found in the fixtures: none of the 9 recorded files have a
 * `schema_version` key at all, and the 5 oldest (`reallife_1`-`5`) have no `markers` key either —
 * `ignoreUnknownKeys` plus field defaults below cover both.
 */
@Serializable
data class RealLifeFixtureExport(
    @SerialName("exported_at_epoch_ms") val exportedAtEpochMs: Long = 0,
    @SerialName("log") val log: RealLifeFixtureLog,
    @SerialName("location_samples") val locationSamples: List<RealLifeFixtureLocation>,
    @SerialName("barometer_samples") val barometerSamples: List<RealLifeFixtureBarometer>,
    @SerialName("markers") val markers: List<RealLifeFixtureMarker> = emptyList()
)

@Serializable
data class RealLifeFixtureLog(
    @SerialName("started_at_epoch_ms") val startedAtEpochMs: Long,
    @SerialName("barometer_available") val barometerAvailable: Boolean
)

@Serializable
data class RealLifeFixtureLocation(
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    @SerialName("altitude_m") val altitudeM: Double? = null,
    @SerialName("speed_mps") val speedMps: Float? = null,
    @SerialName("bearing_deg") val bearingDeg: Float? = null
)

@Serializable
data class RealLifeFixtureBarometer(
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    @SerialName("pressure_hpa") val pressureHpa: Float
)

@Serializable
data class RealLifeFixtureMarker(
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    @SerialName("marker_type") val markerType: String
)

private val fixtureJson = Json { ignoreUnknownKeys = true }

fun parseRealLifeLogFixture(json: String): RealLifeFixtureExport = fixtureJson.decodeFromString(json)

fun RealLifeFixtureLocation.toDetectionSample() = LocationSample(
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    latitude = latitude,
    longitude = longitude,
    altitudeM = altitudeM,
    speedMps = speedMps,
    bearingDeg = bearingDeg
)

fun RealLifeFixtureBarometer.toDetectionSample() = BarometerSample(
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    pressureHpa = pressureHpa
)

/** Loads and parses a fixture file from the test classpath (see `core/build.gradle.kts`'s
 * `sourceSets.test.resources.srcDir` pointing at `docs/data/reallife-samples/`) — note the flat
 * classpath path (no `reallife-samples/` prefix), since that directory itself is the srcDir. */
fun loadRealLifeFixture(fileName: String): RealLifeFixtureExport {
    val stream = object {}.javaClass.classLoader.getResourceAsStream(fileName)
        ?: error("Fixture not found on test classpath: $fileName")
    return parseRealLifeLogFixture(stream.bufferedReader().readText())
}
