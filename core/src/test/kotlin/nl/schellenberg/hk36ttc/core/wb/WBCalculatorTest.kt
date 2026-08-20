package nl.schellenberg.hk36ttc.core.wb

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hand-verified test cases for the generic W&B engine (docs/rekenlogica.md §1).
 *
 * The test [profile] below is entirely SYNTHETIC (fictional registration, round-number
 * empty mass/CG/envelope) — it is NOT the real S/N 36.680 Weighing Report data, which per
 * docs/data/weight_balance_constants.json must come from the owner's own paperwork. It only
 * exists to hand-verify the moment/CG arithmetic and boundary checks in isolation.
 *
 * Expected mass/moment/CG values were computed independently with awk (see session notes)
 * before this test was written, so a bug in WBCalculator can't accidentally match a bug in
 * the expected value. [WbConstantsData.DEFAULT] is used throughout — the app itself never
 * uses that constant at runtime (see its kdoc), but core has no file/asset access of its own.
 */
class WBCalculatorTest {

    private val constants = WbConstantsData.DEFAULT

    private val profileStandardTank = AircraftProfile(
        registration = "PH-TEST",
        emptyMassKg = 560.0,
        emptyMassCgPositionMm = 700.0,
        mtowKg = 770.0,
        cgEnvelopeForwardLimitMm = 600.0,
        cgEnvelopeAftLimitMm = 680.0,
        fuelTankType = FuelTankType.STANDARD_55L
    )

    private val profileLongRangeTank = profileStandardTank.copy(
        fuelTankType = FuelTankType.LONG_RANGE_79L
    )

    private fun assertApprox(expected: Double, actual: Double, message: String, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "$message: expected=$expected actual=$actual")
    }

    @Test
    fun `T1 solo happy path is within envelope and MTOW with no violations`() {
        val result = WBCalculator.calculate(
            profileStandardTank,
            WBInput(pilotKg = 80.0, fuelKg = 30.0),
            constants
        )
        assertApprox(670.0, result.totalMassKg, "total mass")
        assertApprox(425250.0, result.totalMomentKgMm, "total moment")
        assertApprox(634.701492537313, result.cgMm, "cg")
        assertTrue(result.withinMtow)
        assertTrue(result.cgWithinEnvelope)
        assertTrue(result.isSafe)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `T2 dual crew pulls CG forward of the envelope`() {
        val result = WBCalculator.calculate(
            profileStandardTank,
            WBInput(pilotKg = 90.0, copilotKg = 85.0, fuelKg = 20.0),
            constants
        )
        assertApprox(755.0, result.totalMassKg, "total mass")
        assertApprox(431565.0, result.totalMomentKgMm, "total moment")
        assertApprox(571.609271523179, result.cgMm, "cg")
        assertTrue(result.withinMtow)
        assertFalse(result.cgWithinEnvelope)
        assertEquals(1, result.violations.size)
        assertTrue(result.violations.single() is WBViolation.CgOutOfEnvelopeForward)
        assertTrue(result.warnings.isEmpty(), "dual crew should never trigger the solo trim warning")
    }

    @Test
    fun `T3 MTOW exceeded and CG forward violation both reported`() {
        val result = WBCalculator.calculate(
            profileStandardTank,
            WBInput(pilotKg = 100.0, copilotKg = 100.0, fuelKg = 20.0, baggageKg = 5.0),
            constants
        )
        assertApprox(785.0, result.totalMassKg, "total mass")
        assertApprox(438775.0, result.totalMomentKgMm, "total moment")
        assertApprox(558.949044585987, result.cgMm, "cg")
        assertFalse(result.withinMtow)
        assertApprox(-15.0, result.marginToMtowKg, "MTOW margin")
        assertEquals(2, result.violations.size)
        assertTrue(result.violations.any { it is WBViolation.MtowExceeded })
        assertTrue(result.violations.any { it is WBViolation.CgOutOfEnvelopeForward })
    }

    @Test
    fun `T4 per-seat limit exceeded for pilot`() {
        val result = WBCalculator.calculate(
            profileStandardTank,
            WBInput(pilotKg = 115.0, fuelKg = 20.0),
            constants
        )
        assertApprox(695.0, result.totalMassKg, "total mass")
        assertApprox(608.611510791367, result.cgMm, "cg")
        assertTrue(result.cgWithinEnvelope, "cg itself is fine, only the seat limit is violated")
        assertEquals(1, result.violations.size)
        val violation = result.violations.single()
        assertTrue(violation is WBViolation.SeatLimitExceeded && violation.seat == Seat.PILOT)
    }

    @Test
    fun `T5 baggage limit exceeded`() {
        val result = WBCalculator.calculate(
            profileStandardTank,
            WBInput(pilotKg = 80.0, fuelKg = 20.0, baggageKg = 15.0),
            constants
        )
        assertApprox(675.0, result.totalMassKg, "total mass")
        assertApprox(635.385185185185, result.cgMm, "cg")
        assertEquals(1, result.violations.size)
        assertTrue(result.violations.single() is WBViolation.BaggageLimitExceeded)
    }

    @Test
    fun `T6 solo underweight crew triggers trim warning at exact table point`() {
        val result = WBCalculator.calculate(
            profileStandardTank,
            WBInput(pilotKg = 40.0, fuelKg = 20.0),
            constants
        )
        assertApprox(620.0, result.totalMassKg, "total mass")
        assertApprox(664.935483870968, result.cgMm, "cg")
        assertTrue(result.isSafe, "underweight crew is a warning, not a hard violation")
        assertEquals(1, result.warnings.size)
        val warning = result.warnings.single()
        assertTrue(warning is WBWarning.TrimWeightRequired)
        assertApprox(
            5.1, warning.trimMassKg!!,
            "deficit is exactly 15kg, the table's own upper data point (5.1kg trim mass)"
        )
    }

    @Test
    fun `T7 aft envelope violation combined with trim warning, baggage exactly at limit is not a violation`() {
        val result = WBCalculator.calculate(
            profileLongRangeTank,
            WBInput(pilotKg = 40.0, fuelKg = 70.0, baggageKg = 12.0),
            constants
        )
        assertApprox(682.0, result.totalMassKg, "total mass")
        assertApprox(682.240469208211, result.cgMm, "cg")
        assertEquals(1, result.violations.size)
        assertTrue(result.violations.single() is WBViolation.CgOutOfEnvelopeAft)
        assertEquals(1, result.warnings.size, "baggage at exactly 12kg must not add a violation")
    }

    @Test
    fun `trim weight table interpolates linearly between published points`() {
        assertApprox(2.55, constants.trimWeightKg(7.5)!!, "midpoint between 5kg-1.7kg and 10kg-3.4kg")
        assertNull(constants.trimWeightKg(0.0), "no deficit means no trim weight")
        assertNull(constants.trimWeightKg(20.0), "beyond the published 15kg deficit, never extrapolate")
    }

    @Test
    fun `fuel and baggage arms follow the fitted tank type`() {
        assertApprox(727.0, constants.fuelArmMm(FuelTankType.STANDARD_55L), "standard tank arm")
        assertApprox(824.0, constants.fuelArmMm(FuelTankType.LONG_RANGE_79L), "long-range tank arm")
        assertApprox(727.0, constants.baggageArmMm(FuelTankType.STANDARD_55L), "baggage arm = tank arm")
    }
}
