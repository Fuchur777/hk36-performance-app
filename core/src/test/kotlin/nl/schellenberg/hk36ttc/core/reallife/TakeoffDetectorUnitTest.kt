package nl.schellenberg.hk36ttc.core.reallife

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Focused unit tests against small synthetic inputs, isolating the trickier pure-math pieces
 * that the golden-vector real-flight tests ([TakeoffDetectorRealLifeTest]) can't cleanly
 * isolate on their own: circular bearing wraparound, haversine + interpolation distance, and
 * the barometric bucket/baseline/onset pipeline.
 */
class TakeoffDetectorUnitTest {

    // --- Circular bearing wraparound ---

    @Test
    fun `bearing oscillating narrowly across the 0-360 boundary is NOT mistaken for a turn`() {
        // A naive raw-subtraction delta (no wraparound handling) would see 359 to 1 as a ~358
        // swing, when the true angular change is only 2 -- this is the exact bug class the
        // circular-delta helper exists to avoid. Low, near-constant speed throughout (a taxi
        // hold, not a turn), followed by a genuine sustained roll with flat bearing.
        val samples = buildList {
            var t = 0L
            val oscillating = listOf(359f, 1f, 359f, 1f, 359f, 1f, 359f, 1f)
            for (bearing in oscillating) {
                add(LocationSample(t, 52.0, 5.0, null, 2.0f, bearing))
                t += 1_000_000_000L
            }
            // Sustained roll: flat bearing near 0, speed climbing well past the roll-end floor.
            var speed = 3.0f
            repeat(10) {
                add(LocationSample(t, 52.0, 5.0, null, speed, 0f))
                t += 1_000_000_000L
                speed += 2.0f
            }
        }

        val result = TakeoffDetector.detectRollStart(samples)
        assertNotNull(result.elapsedRealtimeNanos)
        assertTrue(
            result.usedFallbackToLogStart,
            "oscillating-but-not-turning bearing should NOT be corroborated as a real turn"
        )
    }

    @Test
    fun `a genuine turn crossing the 0-360 boundary IS detected`() {
        // A real ~40 swing from 350 through 0 to 30, at low speed -- must be detected as a turn
        // despite crossing the wraparound boundary.
        val samples = buildList {
            var t = 0L
            val turning = listOf(350f, 355f, 0f, 10f, 20f, 30f)
            for (bearing in turning) {
                add(LocationSample(t, 52.0, 5.0, null, 2.0f, bearing))
                t += 1_000_000_000L
            }
            var speed = 3.0f
            repeat(10) {
                add(LocationSample(t, 52.0, 5.0, null, speed, 30f))
                t += 1_000_000_000L
                speed += 2.0f
            }
        }

        val result = TakeoffDetector.detectRollStart(samples)
        assertNotNull(result.elapsedRealtimeNanos)
        assertFalse(result.usedFallbackToLogStart, "a genuine 40 swing across the 0/360 boundary should be corroborated as a turn")
    }

    // --- Haversine + interpolation ---

    @Test
    fun `distance interpolates endpoint positions and sums to a known straight-line distance`() {
        // Three fixes 0.001 latitude apart (~111.3m each leg at this latitude). Requesting the
        // distance from the midpoint of leg 1 to the midpoint of leg 2 should sum to
        // approximately one full leg (~111.3m), since the two half-legs sit end to end.
        val samples = listOf(
            LocationSample(0L, 52.000000, 5.0, null, 5f, 0f),
            LocationSample(10_000_000_000L, 52.001000, 5.0, null, 5f, 0f),
            LocationSample(20_000_000_000L, 52.002000, 5.0, null, 5f, 0f)
        )
        val result = TakeoffDetector.calculateTakeoffDistance(samples, rollStartNanos = 5_000_000_000L, toNanos = 15_000_000_000L)

        assertFalse(result.outOfRange)
        val distance = assertNotNull(result.distanceM)
        assertTrue(distance in 105.0..118.0, "expected ~111.3m, was $distance")
    }

    @Test
    fun `distance is flagged out of range rather than extrapolated when a timestamp falls outside the sample window`() {
        val samples = listOf(
            LocationSample(0L, 52.0, 5.0, null, 5f, 0f),
            LocationSample(10_000_000_000L, 52.001, 5.0, null, 5f, 0f)
        )
        val result = TakeoffDetector.calculateTakeoffDistance(samples, rollStartNanos = -1_000_000_000L, toNanos = 5_000_000_000L)
        assertTrue(result.outOfRange)
        assertEquals(null, result.distanceM)
    }

    // --- Barometric bucket / baseline / onset ---

    @Test
    fun `sustained pressure decline after roll start is detected as LIFT_OFF near roll start, height near zero`() {
        // Flat 1000.0 hPa for 10s (covers the 5s pre-roll baseline window ending at rollStart),
        // then a steady 0.5 hPa/s decline -- comfortably above the 0.15 hPa/s onset threshold.
        val rollStartNanos = 10_000_000_000L
        val samples = buildList {
            var t = 0L
            while (t <= 10_000_000_000L) {
                add(BarometerSample(t, 1000.0f))
                t += 200_000_000L // 5 Hz
            }
            var pressure = 1000.0
            while (t <= 25_000_000_000L) {
                pressure -= 0.5 * 0.2
                add(BarometerSample(t, pressure.toFloat()))
                t += 200_000_000L
            }
        }

        val result = TakeoffDetector.detectLiftOffAndFifteenM(
            locationSamples = emptyList(),
            barometerSamples = samples,
            barometerAvailable = true,
            rollStartNanos = rollStartNanos
        )

        val liftOff = assertNotNull(result.liftOffNanos)
        assertTrue(liftOff in 9_000_000_000L..12_000_000_000L, "expected LIFT_OFF near roll start (10s), was ${liftOff / 1e9}s")

        val height = assertNotNull(result.heightAtLiftOffM)
        assertTrue(height in -1.0..3.0, "expected height near 0m at lift-off onset, was $height")

        // Analytically: height reaches 15m at t = 10 + 15/(0.5*8.23) ~= 13.65s.
        val fifteenM = assertNotNull(result.fifteenMNanos)
        assertTrue(fifteenM in 12_000_000_000L..16_000_000_000L, "expected FIFTEEN_M around 13.6s, was ${fifteenM / 1e9}s")
    }

    @Test
    fun `flat pressure with no decline never crosses FIFTEEN_M`() {
        val samples = (0..25_000_000_000L step 200_000_000L).map { BarometerSample(it, 1000.0f) }
        val result = TakeoffDetector.detectLiftOffAndFifteenM(
            locationSamples = emptyList(),
            barometerSamples = samples,
            barometerAvailable = true,
            rollStartNanos = 10_000_000_000L
        )
        assertEquals(null, result.liftOffNanos)
        assertEquals(null, result.fifteenMNanos)
        assertTrue(AltitudeDetectionReason.PressureNeverCrossedThreshold in result.reasons)
    }
}
