package nl.glcillustrious.hk36ttc.core.wb

enum class FuelTankType {
    STANDARD_55L,
    LONG_RANGE_79L
}

/**
 * Per-registration data. CG envelope limits and empty mass figures are NOT generic AFM
 * constants — they must come from the aircraft's own Weighing Report / Mass and Balance
 * Form (see docs/rekenlogica.md §1, docs/data/weight_balance_constants.json "note"). No
 * default MTOW here on purpose: callers must source it from the loaded [WbConstantsData]
 * (or an existing saved profile), never from a literal in code.
 */
data class AircraftProfile(
    val registration: String,
    val emptyMassKg: Double,
    val emptyMassCgPositionMm: Double,
    val mtowKg: Double,
    val cgEnvelopeForwardLimitMm: Double,
    val cgEnvelopeAftLimitMm: Double,
    val fuelTankType: FuelTankType
)
