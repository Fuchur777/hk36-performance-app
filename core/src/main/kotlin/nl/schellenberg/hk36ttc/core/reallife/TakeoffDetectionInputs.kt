package nl.schellenberg.hk36ttc.core.reallife

/**
 * Plain, Room/Android-free mirror of `LocationSampleEntity` (`:app`) — `core` cannot depend on
 * Room, so `:app` maps its entities to these before calling [TakeoffDetector]. Deliberately
 * smaller than the entity: no accuracy fields, since none of the detection algorithms use them.
 */
data class LocationSample(
    val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val speedMps: Float?,
    /** Null whenever the recorder's `hasBearing()` gate was false (speed ≈ 0) — never a
     * misleading 0.0. See [RollStartDetectionReason.NoBearingSwingFound]. */
    val bearingDeg: Float?
)

/** Plain mirror of `BarometerSampleEntity`. */
data class BarometerSample(
    val elapsedRealtimeNanos: Long,
    val pressureHpa: Float
)
