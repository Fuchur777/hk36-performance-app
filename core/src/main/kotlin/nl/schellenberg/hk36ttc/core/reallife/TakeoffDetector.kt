package nl.schellenberg.hk36ttc.core.reallife

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tunable detection constants, grouped as a caller-suppliable defaulted object rather than
 * private consts — same convention as [nl.schellenberg.hk36ttc.core.perf.TowPerformanceCalculator]'s
 * explicit numeric parameters. Every default here is derived from 9 real recorded take-off rolls
 * (`docs/data/reallife-samples/`, see that directory's README.md for the full empirical
 * write-up) — margins are chosen with real headroom below/above the observed values, not tuned
 * to the midpoint of a handful of examples.
 */
data class TakeoffDetectionThresholds(
    /** Real turns swung 64-96°; taxi-roll bearing stayed flat within ~2-4°. */
    val turnBearingSwingDeg: Double = 30.0,
    val turnWindowSeconds: Double = 10.0,
    val turnMaxSpeedMps: Double = 5.0,
    /** Plausibility FLOOR only — never a ceiling. Observed real rolls: 1.4-3.3 m/s², but that's
     * 4 examples and aerotow (slower acceleration, per the project owner's own expectation) has
     * no real recording yet. */
    val minSustainedAccelMps2: Double = 0.5,
    val rollSustainWindowSamples: Int = 3,
    /** A qualifying acceleration run must persist to a real rolling speed, not just clear the
     * floor briefly — "lineup and wait" logs show short pre-roll throttle blips that must not
     * qualify as the roll itself. */
    val minRollEndSpeedMps: Double = 10.0,
    /** Raw barometer samples run ~25Hz and are too noisy for slope/height checks directly —
     * bucket into per-second means first, matching the manual analysis this module's thresholds
     * were derived from. */
    val baroBucketSeconds: Double = 1.0,
    val baroBaselineWindowSeconds: Double = 5.0,
    /** Margin below the observed ~0.4-0.5 hPa/s onset slope at real lift-off. */
    val liftOffDpDtThresholdHpaPerS: Double = 0.15,
    val liftOffSustainBuckets: Int = 2,
    val fifteenMHeightM: Double = 15.0,
    val heightCrossingHysteresisBuckets: Int = 2
) {
    companion object {
        val DEFAULT = TakeoffDetectionThresholds()
    }
}

/**
 * Why [TakeoffDetector.detectRollStart] fell back to reporting the detected roll's own start
 * with no preceding-turn corroboration, rather than a hard failure — the recording may simply
 * have begun already lined up on the runway (no turn to find), which several of the 9 real
 * recordings do.
 */
sealed interface RollStartDetectionReason {
    /** No qualifying bearing swing found before the detected roll — [RollStartResult.usedFallbackToLogStart]. */
    data object NoBearingSwingFound : RollStartDetectionReason
    data object NoSustainedAccelerationFound : RollStartDetectionReason
    data object InsufficientLocationSamples : RollStartDetectionReason
}

data class RollStartResult(
    val elapsedRealtimeNanos: Long?,
    /** True when [elapsedRealtimeNanos] came from the acceleration signal alone, with no
     * preceding bearing-swing corroboration found — lower confidence, not equal confidence, to
     * a turn-corroborated result. */
    val usedFallbackToLogStart: Boolean,
    val failureReasons: List<RollStartDetectionReason>
)

sealed interface AltitudeDetectionReason {
    data object NoBarometerDataAvailable : AltitudeDetectionReason
    data object PressureNeverCrossedThreshold : AltitudeDetectionReason
    /** Precision warning, not a failure — see this module's README-linked findings: the GPS
     * fallback lags the barometric crossing by 5-6s and is noisier, and has no real fixture
     * coverage (all 9 recorded logs have a barometer). */
    data object UsedGpsAltitudeFallback : AltitudeDetectionReason
}

data class AltitudeEventsResult(
    val liftOffNanos: Long?,
    val fifteenMNanos: Long?,
    /** Height AGL at the moment [liftOffNanos] was detected — exposed so callers/tests can
     * sanity-check the P0/height pipeline independent of timing (real recordings: -1.2..+3.7m). */
    val heightAtLiftOffM: Double?,
    /** P0, the ground-reference pressure (or GPS altitude, when falling back) — exposed for
     * debugging/tests. */
    val baselinePressureHpa: Double?,
    val usedGpsAltitudeFallback: Boolean,
    val reasons: List<AltitudeDetectionReason>
)

data class TakeoffDistanceResult(
    val distanceM: Double?,
    /** True when the roll-start/fifteen-m timestamps fell outside the location-sample time
     * range — flagged rather than silently extrapolated, same discipline as
     * [nl.schellenberg.hk36ttc.core.perf.GridInterpolation.clampToRange]. */
    val outOfRange: Boolean
)

data class TakeoffDetectionResult(
    val rollStart: RollStartResult,
    val altitudeEvents: AltitudeEventsResult,
    /** Ground roll only: `ROLL_START` to `LIFT_OFF` — the AFM's "S1"/"grondrol" figure. Arguably
     * the more directly comparable and consequential of the two distances, since it's the pure
     * wheels-on-ground measurement with no climb-performance assumption folded in. */
    val groundRollDistance: TakeoffDistanceResult,
    /** Ground roll plus climb to the 15m/50ft obstacle: `ROLL_START` to `FIFTEEN_M` — the AFM's
     * "S2"/"over 15m obstakel" figure. */
    val totalDistance: TakeoffDistanceResult,
    /** Circular mean of `bearingDeg` between `ROLL_START` and `LIFT_OFF` — the actually-flown
     * ground track, used by Fase 4c as the "runway heading" for a headwind-component
     * calculation instead of a looked-up published runway heading (which this app deliberately
     * never links a recording to). Null when either endpoint or any bearing sample is missing. */
    val rollHeadingDegTrue: Double?
)

/**
 * Detects `ROLL_START`/`LIFT_OFF`/`FIFTEEN_M` and take-off distance from a Fase 4a recording.
 * Every algorithm here is the direct result of analysing 9 real recorded take-off rolls (4 with
 * co-pilot-tapped ground-truth markers) against three competing hypotheses (accelerometer
 * vibration-cutoff, GPS-speed-plateau, barometric pressure) — see
 * `docs/data/reallife-samples/README.md` for the full write-up this module's design and
 * threshold choices are based on. Vibration and GPS-speed-plateau were both explicitly rejected
 * as primary signals (surface-dependent and consistently-late respectively); bearing-swing and
 * barometric pressure were the two that held up across surfaces.
 */
object TakeoffDetector {

    /** ~8.23 m per hPa near sea level — a universal atmospheric constant, not AFM-tunable, kept
     * as its own literal here rather than shared, same convention as
     * [nl.schellenberg.hk36ttc.core.metar.PressureAltitude]'s own `METERS_PER_HPA`. */
    private const val METERS_PER_HPA = 8.23

    fun detectRollStart(
        locationSamples: List<LocationSample>,
        thresholds: TakeoffDetectionThresholds = TakeoffDetectionThresholds.DEFAULT
    ): RollStartResult = detectRollStartSorted(locationSamples.sortedBy { it.elapsedRealtimeNanos }, thresholds)

    /** [locationSamples] must already be sorted by [LocationSample.elapsedRealtimeNanos] --
     * [detect] sorts once and reuses that single sort across every step below, rather than each
     * step (this one included, previously) re-sorting the same list from scratch. [detectRollStart]
     * is the public, sort-for-you entry point for callers that don't already have a sorted list. */
    private fun detectRollStartSorted(sorted: List<LocationSample>, thresholds: TakeoffDetectionThresholds): RollStartResult {
        if (sorted.size < thresholds.rollSustainWindowSamples + 1) {
            return RollStartResult(null, usedFallbackToLogStart = false, failureReasons = listOf(RollStartDetectionReason.InsufficientLocationSamples))
        }

        // Turn search runs over the WHOLE log first (naturally bounded to genuine low-speed
        // ground turns by the turnMaxSpeedMps gate -- airborne bearing changes never qualify),
        // THEN acceleration-run search is bounded to start only after the turn ends. Originally
        // this ran the other way (find the LAST sustained-acceleration run anywhere in the log,
        // then look backward for a turn) -- but a log can contain a second, later sustained
        // acceleration segment during climb-out/manoeuvring (GPS ground speed keeps rising well
        // past actual lift-off, a finding from this module's own real-flight analysis), and
        // "last run in the whole log" would grab THAT instead of the real roll. Confirmed by a
        // real failure on reallife_7: the old approach detected "roll start" 43s after the real
        // marker, landing inside the log's post-liftoff tail.
        val turnEndIndex = findLastQualifyingTurnEndIndex(sorted, thresholds)
        val searchFrom = (turnEndIndex ?: -1) + 1

        val runs = findSustainedAccelerationRuns(sorted, thresholds, searchFrom)
        if (runs.isEmpty()) {
            return RollStartResult(null, usedFallbackToLogStart = false, failureReasons = listOf(RollStartDetectionReason.NoSustainedAccelerationFound))
        }
        // The roll immediately follows the turn (or, with no turn found, is the first sustained
        // acceleration in the log) -- FIRST qualifying run in the bounded search region, not last.
        val rollStartIndex = runs.first().startIndex
        val rollStartNanos = sorted[rollStartIndex].elapsedRealtimeNanos

        return if (turnEndIndex != null) {
            RollStartResult(rollStartNanos, usedFallbackToLogStart = false, failureReasons = emptyList())
        } else {
            RollStartResult(rollStartNanos, usedFallbackToLogStart = true, failureReasons = listOf(RollStartDetectionReason.NoBearingSwingFound))
        }
    }

    fun detectLiftOffAndFifteenM(
        locationSamples: List<LocationSample>,
        barometerSamples: List<BarometerSample>,
        barometerAvailable: Boolean,
        rollStartNanos: Long,
        thresholds: TakeoffDetectionThresholds = TakeoffDetectionThresholds.DEFAULT
    ): AltitudeEventsResult = detectLiftOffAndFifteenMSorted(
        locationSamples.sortedBy { it.elapsedRealtimeNanos },
        barometerSamples.sortedBy { it.elapsedRealtimeNanos },
        barometerAvailable, rollStartNanos, thresholds
    )

    /** [sortedLocationSamples]/[sortedBarometerSamples] must already be sorted -- see
     * [detectRollStartSorted]'s KDoc for why. */
    private fun detectLiftOffAndFifteenMSorted(
        sortedLocationSamples: List<LocationSample>,
        sortedBarometerSamples: List<BarometerSample>,
        barometerAvailable: Boolean,
        rollStartNanos: Long,
        thresholds: TakeoffDetectionThresholds
    ): AltitudeEventsResult {
        val useBarometer = barometerAvailable && sortedBarometerSamples.isNotEmpty()
        val series = if (useBarometer) {
            bucketBySeconds(
                sortedBarometerSamples.map { it.elapsedRealtimeNanos to it.pressureHpa.toDouble() },
                thresholds.baroBucketSeconds
            )
        } else {
            bucketBySeconds(
                sortedLocationSamples.mapNotNull { s -> s.altitudeM?.let { s.elapsedRealtimeNanos to it } },
                thresholds.baroBucketSeconds
            )
        }
        if (series.isEmpty()) {
            return AltitudeEventsResult(null, null, null, null, usedGpsAltitudeFallback = !useBarometer, reasons = listOf(AltitudeDetectionReason.NoBarometerDataAvailable))
        }

        val baselineWindowStart = rollStartNanos - (thresholds.baroBaselineWindowSeconds * 1e9).toLong()
        val baseline = series.filter { it.first in baselineWindowStart..rollStartNanos }
        if (baseline.isEmpty()) {
            return AltitudeEventsResult(null, null, null, null, usedGpsAltitudeFallback = !useBarometer, reasons = listOf(AltitudeDetectionReason.NoBarometerDataAvailable))
        }
        val p0 = baseline.map { it.second }.average()

        // Unified sign convention: height increases while climbing, regardless of source.
        val heights = if (useBarometer) {
            series.map { (nanos, p) -> nanos to (p0 - p) * METERS_PER_HPA }
        } else {
            series.map { (nanos, alt) -> nanos to (alt - p0) }
        }
        val postRoll = heights.filter { it.first >= rollStartNanos }

        val rateThresholdMps = thresholds.liftOffDpDtThresholdHpaPerS * METERS_PER_HPA
        val liftOffNanos = findSustainedRateOnset(postRoll, rateThresholdMps, thresholds.liftOffSustainBuckets)
        val heightAtLiftOff = liftOffNanos?.let { t -> postRoll.firstOrNull { it.first == t }?.second }
        val fifteenMNanos = findHeightCrossing(postRoll, thresholds.fifteenMHeightM, thresholds.heightCrossingHysteresisBuckets)

        val reasons = mutableListOf<AltitudeDetectionReason>()
        if (!useBarometer) reasons += AltitudeDetectionReason.UsedGpsAltitudeFallback
        if (liftOffNanos == null || fifteenMNanos == null) reasons += AltitudeDetectionReason.PressureNeverCrossedThreshold

        return AltitudeEventsResult(
            liftOffNanos = liftOffNanos,
            fifteenMNanos = fifteenMNanos,
            heightAtLiftOffM = heightAtLiftOff,
            baselinePressureHpa = p0,
            usedGpsAltitudeFallback = !useBarometer,
            reasons = reasons
        )
    }

    /** Generic haversine + interpolation distance between [rollStartNanos] and [toNanos] --
     * used for both [TakeoffDetectionResult.groundRollDistance] (`toNanos` = `LIFT_OFF`) and
     * [TakeoffDetectionResult.totalDistance] (`toNanos` = `FIFTEEN_M`), since the underlying math
     * doesn't care which event the end timestamp represents. */
    fun calculateTakeoffDistance(
        locationSamples: List<LocationSample>,
        rollStartNanos: Long,
        toNanos: Long
    ): TakeoffDistanceResult = calculateTakeoffDistanceSorted(locationSamples.sortedBy { it.elapsedRealtimeNanos }, rollStartNanos, toNanos)

    /** [sorted] must already be sorted -- see [detectRollStartSorted]'s KDoc for why. */
    private fun calculateTakeoffDistanceSorted(sorted: List<LocationSample>, rollStartNanos: Long, toNanos: Long): TakeoffDistanceResult {
        if (sorted.isEmpty() || rollStartNanos >= toNanos ||
            rollStartNanos < sorted.first().elapsedRealtimeNanos || toNanos > sorted.last().elapsedRealtimeNanos
        ) {
            return TakeoffDistanceResult(null, outOfRange = true)
        }

        val startPoint = interpolatePosition(sorted, rollStartNanos) ?: return TakeoffDistanceResult(null, outOfRange = true)
        val endPoint = interpolatePosition(sorted, toNanos) ?: return TakeoffDistanceResult(null, outOfRange = true)
        val between = sorted.filter { it.elapsedRealtimeNanos in rollStartNanos..toNanos }
            .map { it.latitude to it.longitude }

        val points = listOf(startPoint) + between + listOf(endPoint)
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
        }
        return TakeoffDistanceResult(total, outOfRange = false)
    }

    /** Convenience wrapper composing the three functions above. Sorts [locationSamples]/
     * [barometerSamples] once here and threads the sorted lists through every step below --
     * each step used to sort its own input independently (up to 4 redundant O(n log n) sorts of
     * the same data per call), pure wasted work since the input order never changes between
     * steps within one [detect] call. */
    fun detect(
        locationSamples: List<LocationSample>,
        barometerSamples: List<BarometerSample>,
        barometerAvailable: Boolean,
        thresholds: TakeoffDetectionThresholds = TakeoffDetectionThresholds.DEFAULT
    ): TakeoffDetectionResult {
        val sortedLocation = locationSamples.sortedBy { it.elapsedRealtimeNanos }
        val sortedBarometer = barometerSamples.sortedBy { it.elapsedRealtimeNanos }

        val rollStart = detectRollStartSorted(sortedLocation, thresholds)
        val rollStartNanos = rollStart.elapsedRealtimeNanos

        val altitudeEvents = if (rollStartNanos != null) {
            detectLiftOffAndFifteenMSorted(sortedLocation, sortedBarometer, barometerAvailable, rollStartNanos, thresholds)
        } else {
            AltitudeEventsResult(null, null, null, null, usedGpsAltitudeFallback = false, reasons = emptyList())
        }

        val liftOffNanos = altitudeEvents.liftOffNanos
        val fifteenMNanos = altitudeEvents.fifteenMNanos

        val groundRollDistance = if (rollStartNanos != null && liftOffNanos != null) {
            calculateTakeoffDistanceSorted(sortedLocation, rollStartNanos, liftOffNanos)
        } else {
            TakeoffDistanceResult(null, outOfRange = false)
        }
        val totalDistance = if (rollStartNanos != null && fifteenMNanos != null) {
            calculateTakeoffDistanceSorted(sortedLocation, rollStartNanos, fifteenMNanos)
        } else {
            TakeoffDistanceResult(null, outOfRange = false)
        }

        val rollHeadingDegTrue = if (rollStartNanos != null && liftOffNanos != null) {
            circularMeanBearing(locationSamples, rollStartNanos, liftOffNanos)
        } else {
            null
        }

        return TakeoffDetectionResult(rollStart, altitudeEvents, groundRollDistance, totalDistance, rollHeadingDegTrue)
    }

    // --- ROLL_START helpers ---

    private data class SampleRun(val startIndex: Int, val endIndex: Int)

    /** Finds every window of ≥[TakeoffDetectionThresholds.rollSustainWindowSamples] consecutive
     * samples whose speed keeps increasing at ≥[TakeoffDetectionThresholds.minSustainedAccelMps2],
     * ending at a real rolling speed (≥[TakeoffDetectionThresholds.minRollEndSpeedMps]) — not a
     * brief pre-roll throttle blip. Only considers transitions from index ≥[searchFrom] onward
     * (see [detectRollStart]'s KDoc for why the search is bounded rather than scanning the whole
     * log). Callers take the FIRST run in the (bounded) result, not the last. */
    private fun findSustainedAccelerationRuns(sorted: List<LocationSample>, thresholds: TakeoffDetectionThresholds, searchFrom: Int): List<SampleRun> {
        val runs = mutableListOf<SampleRun>()
        var runStart: Int? = null
        var consecutive = 0

        fun flush(endIndex: Int) {
            val start = runStart
            if (start != null && consecutive >= thresholds.rollSustainWindowSamples &&
                (sorted[endIndex].speedMps ?: 0f) >= thresholds.minRollEndSpeedMps
            ) {
                runs += SampleRun(start, endIndex)
            }
            runStart = null
            consecutive = 0
        }

        for (i in maxOf(searchFrom + 1, 1) until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val prevSpeed = prev.speedMps
            val currSpeed = curr.speedMps
            val dtSeconds = (curr.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9

            val accelOk = prevSpeed != null && currSpeed != null && dtSeconds > 0.0 &&
                (currSpeed - prevSpeed) / dtSeconds >= thresholds.minSustainedAccelMps2

            if (accelOk) {
                if (runStart == null) runStart = i - 1
                consecutive++
            } else {
                flush(i - 1)
            }
        }
        flush(sorted.lastIndex)
        return runs
    }

    /** Median-of-3 by circular distance, applied only to the non-null-bearing subsequence
     * (bearing is null while stationary — no gap to fill there). Robust against the real
     * single-sample bearing outliers already documented in
     * `docs/data/reallife-samples/README.md` (e.g. a 241° spike between two ~278° readings). */
    private fun medianFilteredBearings(samples: List<LocationSample>): List<Pair<Int, Double>> {
        val nonNull = samples.withIndex().mapNotNull { (i, s) -> s.bearingDeg?.let { i to it.toDouble() } }
        return nonNull.indices.map { i ->
            val prev = nonNull.getOrNull(i - 1)?.second
            val current = nonNull[i].second
            val next = nonNull.getOrNull(i + 1)?.second
            // Only interior points (both neighbors present) get median-filtered -- the first/last
            // point of the non-null run keeps its own original value rather than being pulled
            // toward a neighbor, which a size-based fallback used to do incorrectly.
            val filtered = if (prev != null && next != null) medianOfThreeCircular(prev, current, next) else current
            nonNull[i].first to filtered
        }
    }

    private fun medianOfThreeCircular(a: Double, b: Double, c: Double): Double =
        listOf(
            a to (circularAbsDistance(a, b) + circularAbsDistance(a, c)),
            b to (circularAbsDistance(b, a) + circularAbsDistance(b, c)),
            c to (circularAbsDistance(c, a) + circularAbsDistance(c, b))
        ).minBy { it.second }.first

    private data class BearingEntry(val sampleIndex: Int, val nanos: Long, val bearingDeg: Double, val speedMps: Float)

    /** Scans the WHOLE log for candidate turn-window starts, extending forward while speed stays
     * ≤[TakeoffDetectionThresholds.turnMaxSpeedMps] and within
     * [TakeoffDetectionThresholds.turnWindowSeconds], accumulating circular bearing swing. Not
     * bounded to "before a pre-identified roll" — the [TakeoffDetectionThresholds.turnMaxSpeedMps]
     * gate alone already excludes airborne/fast-taxi bearing changes from ever qualifying, so a
     * genuine ground turn is found wherever it happens. Returns the sample INDEX of the LATEST
     * (closest to roll start) window whose accumulated swing cleared
     * [TakeoffDetectionThresholds.turnBearingSwingDeg] — used to bound where the roll-start search
     * begins, see [detectRollStart]. */
    private fun findLastQualifyingTurnEndIndex(sorted: List<LocationSample>, thresholds: TakeoffDetectionThresholds): Int? {
        val entries = medianFilteredBearings(sorted)
            .map { (idx, bearing) -> BearingEntry(idx, sorted[idx].elapsedRealtimeNanos, bearing, sorted[idx].speedMps ?: 0f) }

        var lastQualifyingIndex: Int? = null
        for (start in entries.indices) {
            if (entries[start].speedMps > thresholds.turnMaxSpeedMps) continue
            var cumulative = 0.0
            for (end in start + 1 until entries.size) {
                val e = entries[end]
                if (e.speedMps > thresholds.turnMaxSpeedMps) break
                val windowSeconds = (e.nanos - entries[start].nanos) / 1e9
                if (windowSeconds > thresholds.turnWindowSeconds) break
                cumulative += circularAbsDistance(entries[end - 1].bearingDeg, e.bearingDeg)
                if (cumulative >= thresholds.turnBearingSwingDeg) {
                    // Record only the FIRST point this window crosses the threshold, then stop
                    // extending it -- continuing to scan would keep re-qualifying every
                    // subsequent flat-bearing point too (a stationary hold or the early roll
                    // itself both hold bearing flat, same as a completed turn does), creeping
                    // the detected turn-end forward into the roll. Track the true maximum
                    // across all start candidates, not just the last one iterated.
                    lastQualifyingIndex = maxOf(lastQualifyingIndex ?: -1, e.sampleIndex)
                    break
                }
            }
        }
        return lastQualifyingIndex
    }

    private fun circularAbsDistance(a: Double, b: Double): Double {
        var d = (b - a) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return abs(d)
    }

    // --- LIFT_OFF / FIFTEEN_M helpers ---

    private fun bucketBySeconds(points: List<Pair<Long, Double>>, bucketSeconds: Double): List<Pair<Long, Double>> {
        if (points.isEmpty()) return emptyList()
        val bucketNanos = (bucketSeconds * 1e9).toLong()
        val t0 = points.first().first
        return points.groupBy { (it.first - t0) / bucketNanos }
            .toSortedMap()
            .map { (_, group) -> group.map { it.first }.average().toLong() to group.map { it.second }.average() }
    }

    /** First point where the rate of climb stays ≥[thresholdMps] for [sustainBuckets] consecutive
     * buckets — the onset of a sustained climb, not a single noisy bucket. Returns the START of
     * that sustained run (the moment the climb actually began), matching this module's own
     * "the sustained slope kink IS the lift-off moment" finding. */
    private fun findSustainedRateOnset(heights: List<Pair<Long, Double>>, thresholdMps: Double, sustainBuckets: Int): Long? {
        if (heights.size < sustainBuckets + 1) return null
        var consecutive = 0
        for (i in 1 until heights.size) {
            val (t0, h0) = heights[i - 1]
            val (t1, h1) = heights[i]
            val dt = (t1 - t0) / 1e9
            val rate = if (dt > 0) (h1 - h0) / dt else 0.0
            if (rate >= thresholdMps) {
                consecutive++
                if (consecutive >= sustainBuckets) return heights[i - consecutive].first
            } else {
                consecutive = 0
            }
        }
        return null
    }

    /** First point where height stays ≥[thresholdM] for [hysteresisBuckets] consecutive buckets —
     * avoids single-bucket noise chattering across the boundary. */
    private fun findHeightCrossing(heights: List<Pair<Long, Double>>, thresholdM: Double, hysteresisBuckets: Int): Long? {
        var consecutive = 0
        for (i in heights.indices) {
            if (heights[i].second >= thresholdM) {
                consecutive++
                if (consecutive >= hysteresisBuckets) return heights[i - hysteresisBuckets + 1].first
            } else {
                consecutive = 0
            }
        }
        return null
    }

    /** Circular mean of the bearing samples in `[fromNanos, toNanos]` — a plain arithmetic mean
     * would break near the 0/360 boundary, same reasoning as [circularAbsDistance]. Averaged via
     * the sum of unit vectors (sin/cos), the standard circular-mean construction. */
    private fun circularMeanBearing(locationSamples: List<LocationSample>, fromNanos: Long, toNanos: Long): Double? {
        val bearings = locationSamples
            .filter { it.elapsedRealtimeNanos in fromNanos..toNanos }
            .mapNotNull { it.bearingDeg?.toDouble() }
        if (bearings.isEmpty()) return null
        val sinSum = bearings.sumOf { sin(Math.toRadians(it)) }
        val cosSum = bearings.sumOf { cos(Math.toRadians(it)) }
        val meanDeg = Math.toDegrees(atan2(sinSum, cosSum))
        return if (meanDeg < 0) meanDeg + 360.0 else meanDeg
    }

    // --- Distance helpers ---

    private fun interpolatePosition(sorted: List<LocationSample>, atNanos: Long): Pair<Double, Double>? {
        val before = sorted.lastOrNull { it.elapsedRealtimeNanos <= atNanos } ?: return null
        val after = sorted.firstOrNull { it.elapsedRealtimeNanos >= atNanos } ?: return null
        if (before.elapsedRealtimeNanos == after.elapsedRealtimeNanos) return before.latitude to before.longitude
        val t = (atNanos - before.elapsedRealtimeNanos).toDouble() / (after.elapsedRealtimeNanos - before.elapsedRealtimeNanos)
        return (before.latitude + t * (after.latitude - before.latitude)) to (before.longitude + t * (after.longitude - before.longitude))
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val sinDPhi = sin(dPhi / 2)
        val sinDLambda = sin(dLambda / 2)
        val a = sinDPhi * sinDPhi + cos(phi1) * cos(phi2) * sinDLambda * sinDLambda
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
