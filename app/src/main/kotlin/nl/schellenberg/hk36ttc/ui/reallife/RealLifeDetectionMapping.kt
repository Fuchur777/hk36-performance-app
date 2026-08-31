package nl.schellenberg.hk36ttc.ui.reallife

import nl.schellenberg.hk36ttc.core.reallife.BarometerSample
import nl.schellenberg.hk36ttc.core.reallife.LocationSample
import nl.schellenberg.hk36ttc.data.local.BarometerSampleEntity
import nl.schellenberg.hk36ttc.data.local.LocationSampleEntity

/** Maps Room entities to `core.reallife`'s plain detection input types — `core` cannot depend on
 * Room, so this conversion happens app-side, in memory, with no JSON involved (unlike the
 * `core/src/test`-only fixture parser used to load real recordings as golden-vector test data). */
fun LocationSampleEntity.toDetectionSample() = LocationSample(
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    latitude = latitude,
    longitude = longitude,
    altitudeM = altitudeM,
    speedMps = speedMps,
    bearingDeg = bearingDeg
)

fun BarometerSampleEntity.toDetectionSample() = BarometerSample(
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    pressureHpa = pressureHpa
)
