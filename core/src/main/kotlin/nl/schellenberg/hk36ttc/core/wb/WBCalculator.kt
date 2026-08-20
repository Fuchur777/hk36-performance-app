package nl.schellenberg.hk36ttc.core.wb

/**
 * User-supplied loading for one calculation. Fuel is entered as a weight (kg), not volume —
 * rekenlogica.md §1.1 treats every loading post as a weight, not a volume.
 */
data class WBInput(
    val pilotKg: Double,
    val copilotKg: Double = 0.0,
    val fuelKg: Double,
    val baggageKg: Double = 0.0
)

data class WBItem(val label: String, val massKg: Double, val armMm: Double) {
    val momentKgMm: Double get() = massKg * armMm
}

enum class Seat { PILOT, COPILOT }

/**
 * Why a loading fails a hard limit — a typed reason rather than a literal message, since
 * `core` is a plain Kotlin/JVM module with no Android resources of its own to localize a
 * message string with. The app layer maps each case to a localized string (see
 * `WbScreen.kt`'s `WbResultCard`).
 */
sealed interface WBViolation {
    data class MtowExceeded(val totalMassKg: Double, val mtowKg: Double) : WBViolation
    data class SeatLimitExceeded(val seat: Seat, val massKg: Double, val maxKg: Double) : WBViolation
    data class BaggageLimitExceeded(val massKg: Double, val maxKg: Double) : WBViolation
    data class CgOutOfEnvelopeForward(val cgMm: Double, val forwardLimitMm: Double) : WBViolation
    data class CgOutOfEnvelopeAft(val cgMm: Double, val aftLimitMm: Double) : WBViolation
}

/** Same rationale as [WBViolation]: typed, not a literal message. */
sealed interface WBWarning {
    data class TrimWeightRequired(val usefulLoadOnSeatsKg: Double, val minRequiredKg: Double, val trimMassKg: Double?) : WBWarning
}

data class WBResult(
    val items: List<WBItem>,
    val totalMassKg: Double,
    val totalMomentKgMm: Double,
    val cgMm: Double,
    val marginToMtowKg: Double,
    val withinMtow: Boolean,
    val cgWithinEnvelope: Boolean,
    val marginForwardMm: Double,
    val marginAftMm: Double,
    val violations: List<WBViolation>,
    val warnings: List<WBWarning>
) {
    val isSafe: Boolean get() = violations.isEmpty()
}

/**
 * Generic weight & balance engine — works for any registration whose [AircraftProfile] is
 * supplied by the caller. See docs/rekenlogica.md §1 for the source formulas.
 */
object WBCalculator {

    fun calculate(profile: AircraftProfile, input: WBInput, constants: WbConstantsData): WBResult {
        val items = mutableListOf<WBItem>()
        items += WBItem("Empty mass", profile.emptyMassKg, profile.emptyMassCgPositionMm)
        items += WBItem("Pilot", input.pilotKg, constants.seatArmMm)
        if (input.copilotKg > 0.0) {
            items += WBItem("Copilot", input.copilotKg, constants.seatArmMm)
        }
        items += WBItem("Fuel", input.fuelKg, constants.fuelArmMm(profile.fuelTankType))
        if (input.baggageKg > 0.0) {
            items += WBItem("Baggage", input.baggageKg, constants.baggageArmMm(profile.fuelTankType))
        }

        val totalMass = items.sumOf { it.massKg }
        val totalMoment = items.sumOf { it.momentKgMm }
        val cg = if (totalMass > 0.0) totalMoment / totalMass else 0.0

        val violations = mutableListOf<WBViolation>()
        val warnings = mutableListOf<WBWarning>()

        val marginToMtow = profile.mtowKg - totalMass
        if (totalMass > profile.mtowKg) {
            violations += WBViolation.MtowExceeded(totalMassKg = totalMass, mtowKg = profile.mtowKg)
        }

        if (input.pilotKg > constants.maxUsefulLoadPerSeatKg) {
            violations += WBViolation.SeatLimitExceeded(Seat.PILOT, input.pilotKg, constants.maxUsefulLoadPerSeatKg)
        }
        if (input.copilotKg > constants.maxUsefulLoadPerSeatKg) {
            violations += WBViolation.SeatLimitExceeded(Seat.COPILOT, input.copilotKg, constants.maxUsefulLoadPerSeatKg)
        }
        if (input.baggageKg > constants.maxBaggageKg) {
            violations += WBViolation.BaggageLimitExceeded(input.baggageKg, constants.maxBaggageKg)
        }

        val marginForward = cg - profile.cgEnvelopeForwardLimitMm
        val marginAft = profile.cgEnvelopeAftLimitMm - cg
        val cgWithinEnvelope = marginForward >= 0.0 && marginAft >= 0.0
        if (marginForward < 0.0) {
            violations += WBViolation.CgOutOfEnvelopeForward(cg, profile.cgEnvelopeForwardLimitMm)
        }
        if (marginAft < 0.0) {
            violations += WBViolation.CgOutOfEnvelopeAft(cg, profile.cgEnvelopeAftLimitMm)
        }

        val soloFlight = input.copilotKg <= 0.0
        val usefulLoadOnSeats = input.pilotKg + input.copilotKg
        if (soloFlight && usefulLoadOnSeats < constants.minUsefulLoadOnSeatsKg) {
            val deficit = constants.minUsefulLoadOnSeatsKg - usefulLoadOnSeats
            val trimMass = constants.trimWeightKg(deficit)
            warnings += WBWarning.TrimWeightRequired(
                usefulLoadOnSeatsKg = usefulLoadOnSeats,
                minRequiredKg = constants.minUsefulLoadOnSeatsKg,
                trimMassKg = trimMass
            )
        }

        return WBResult(
            items = items,
            totalMassKg = totalMass,
            totalMomentKgMm = totalMoment,
            cgMm = cg,
            marginToMtowKg = marginToMtow,
            withinMtow = totalMass <= profile.mtowKg,
            cgWithinEnvelope = cgWithinEnvelope,
            marginForwardMm = marginForward,
            marginAftMm = marginAft,
            violations = violations,
            warnings = warnings
        )
    }
}
