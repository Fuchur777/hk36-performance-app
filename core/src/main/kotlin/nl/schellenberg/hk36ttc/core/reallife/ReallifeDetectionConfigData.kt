package nl.schellenberg.hk36ttc.core.reallife

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Runtime-loaded [TakeoffDetector] tuning (rekenlogica.md's "never hardcode a calc value in
 * Kotlin" rule) -- same pattern as [nl.schellenberg.hk36ttc.core.metar.MetarConfigData]. Field
 * meanings and empirical basis are documented on [TakeoffDetectionThresholds], which this maps
 * onto via [toThresholds]; defaults here match [TakeoffDetectionThresholds]'s own Kotlin defaults
 * only for test/first-run-seeding parity -- the runtime path always reads the JSON asset. */
@Serializable
data class ReallifeDetectionConfigData(
    @SerialName("turn_bearing_swing_deg") val turnBearingSwingDeg: Double = 30.0,
    @SerialName("turn_window_seconds") val turnWindowSeconds: Double = 10.0,
    @SerialName("turn_max_speed_mps") val turnMaxSpeedMps: Double = 5.0,
    @SerialName("min_sustained_accel_mps2") val minSustainedAccelMps2: Double = 0.5,
    @SerialName("roll_sustain_window_samples") val rollSustainWindowSamples: Int = 3,
    @SerialName("min_roll_end_speed_mps") val minRollEndSpeedMps: Double = 10.0,
    @SerialName("baro_bucket_seconds") val baroBucketSeconds: Double = 1.0,
    @SerialName("baro_baseline_window_seconds") val baroBaselineWindowSeconds: Double = 5.0,
    @SerialName("lift_off_dp_dt_threshold_hpa_per_s") val liftOffDpDtThresholdHpaPerS: Double = 0.15,
    @SerialName("lift_off_sustain_buckets") val liftOffSustainBuckets: Int = 2,
    @SerialName("fifteen_m_height_m") val fifteenMHeightM: Double = 15.0,
    @SerialName("height_crossing_hysteresis_buckets") val heightCrossingHysteresisBuckets: Int = 2
) {
    fun toThresholds(): TakeoffDetectionThresholds = TakeoffDetectionThresholds(
        turnBearingSwingDeg = turnBearingSwingDeg,
        turnWindowSeconds = turnWindowSeconds,
        turnMaxSpeedMps = turnMaxSpeedMps,
        minSustainedAccelMps2 = minSustainedAccelMps2,
        rollSustainWindowSamples = rollSustainWindowSamples,
        minRollEndSpeedMps = minRollEndSpeedMps,
        baroBucketSeconds = baroBucketSeconds,
        baroBaselineWindowSeconds = baroBaselineWindowSeconds,
        liftOffDpDtThresholdHpaPerS = liftOffDpDtThresholdHpaPerS,
        liftOffSustainBuckets = liftOffSustainBuckets,
        fifteenMHeightM = fifteenMHeightM,
        heightCrossingHysteresisBuckets = heightCrossingHysteresisBuckets
    )

    companion object {
        /** Fallback only for tests/first-run seeding -- never the runtime path (see
         * CalculationDataStore), same convention as [nl.schellenberg.hk36ttc.core.metar.MetarConfigData.DEFAULT]. */
        val DEFAULT = ReallifeDetectionConfigData()
    }
}

private val reallifeDetectionConfigJson = Json { ignoreUnknownKeys = true }

fun parseReallifeDetectionConfigData(json: String): ReallifeDetectionConfigData =
    reallifeDetectionConfigJson.decodeFromString(ReallifeDetectionConfigData.serializer(), json)
