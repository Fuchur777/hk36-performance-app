package nl.glcillustrious.hk36ttc.core.wb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Generic AFM-wide constants, identical for every HK36 TTC regardless of registration.
 *
 * This is loaded at runtime from a JSON file on the device (see [parseWbConstants]) rather
 * than hardcoded, so the club can update the tables (a new AFM revision, a corrected trim
 * table, ...) by editing that file with a plain JSON editor and restarting the app — no new
 * build or app-store release. The app seeds that file from a bundled copy on first launch;
 * see the app module's `CalculationDataStore`.
 *
 * Field names/shape mirror docs/data/weight_balance_constants.json exactly (AFM 3.01.20-E
 * Rev. 4, section 6.4-6.8) so the file can be hand-edited without needing a schema doc.
 */
@Serializable
data class WbConstantsData(
    @SerialName("mtow_kg") val defaultMtowKg: Double,
    @SerialName("max_non_lifting_parts_mass_kg") val maxNonLiftingPartsMassKg: Double,
    @SerialName("min_useful_load_on_seats_kg") val minUsefulLoadOnSeatsKg: Double,
    @SerialName("max_useful_load_per_seat_kg") val maxUsefulLoadPerSeatKg: Double,
    @SerialName("max_baggage_kg") val maxBaggageKg: Double,
    @SerialName("fuel_density_kg_per_l") val fuelDensityKgPerL: Double,
    @SerialName("fuel_tank_capacity_l") val fuelTankCapacityL: FuelTankCapacityL,
    @SerialName("arms_mm_aft_of_datum") val armsMmAftOfDatum: ArmsMmAftOfDatum,
    @SerialName("trim_weights_kg") val trimWeightsKg: TrimWeightsKg
) {
    @Serializable
    data class FuelTankCapacityL(
        @SerialName("standard_55l") val standard55l: Double,
        @SerialName("long_range_79l") val longRange79l: Double
    )

    @Serializable
    data class ArmsMmAftOfDatum(
        @SerialName("seat_payload") val seatPayload: Double,
        @SerialName("fuel_tank_standard_55l") val fuelTankStandard55l: Double,
        @SerialName("fuel_tank_long_range_79l") val fuelTankLongRange79l: Double
    )

    @Serializable
    data class TrimWeightsKg(val table: List<TrimPoint>)

    @Serializable
    data class TrimPoint(
        @SerialName("deficit_kg") val deficitKg: Double,
        @SerialName("trim_mass_kg") val trimMassKg: Double
    )

    val seatArmMm: Double get() = armsMmAftOfDatum.seatPayload

    fun fuelArmMm(tankType: FuelTankType): Double = when (tankType) {
        FuelTankType.STANDARD_55L -> armsMmAftOfDatum.fuelTankStandard55l
        FuelTankType.LONG_RANGE_79L -> armsMmAftOfDatum.fuelTankLongRange79l
    }

    /** Baggage arm equals the fitted fuel tank's arm (rekenlogica.md §1.1). */
    fun baggageArmMm(tankType: FuelTankType): Double = fuelArmMm(tankType)

    /** The fitted tank's usable capacity — used to cap how many liters can be entered. */
    fun tankCapacityLiters(tankType: FuelTankType): Double = when (tankType) {
        FuelTankType.STANDARD_55L -> fuelTankCapacityL.standard55l
        FuelTankType.LONG_RANGE_79L -> fuelTankCapacityL.longRange79l
    }

    /** Fuel is entered in liters (what a pilot reads off a dipstick/gauge); the W&B engine
     * itself works in kg, so this is the one place that conversion happens. */
    fun fuelKgFromLiters(liters: Double): Double = liters * fuelDensityKgPerL

    /**
     * Linear interpolation over the trim-weight table, generalized over however many
     * points [trimWeightsKg] currently holds (the club may add points later by editing the
     * JSON). Returns null outside the published deficit range rather than silently
     * extrapolating (consistent with the never-extrapolate rule in rekenlogica.md §2.1).
     */
    fun trimWeightKg(deficitKg: Double): Double? {
        if (deficitKg <= 0.0) return null
        val points = (listOf(TrimPoint(0.0, 0.0)) + trimWeightsKg.table).sortedBy { it.deficitKg }
        if (deficitKg > points.last().deficitKg) return null
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (deficitKg in p1.deficitKg..p2.deficitKg) {
                return p1.trimMassKg + (p2.trimMassKg - p1.trimMassKg) *
                    (deficitKg - p1.deficitKg) / (p2.deficitKg - p1.deficitKg)
            }
        }
        return points.last().trimMassKg
    }

    companion object {
        /**
         * Hardcoded copy of docs/data/weight_balance_constants.json (AFM 3.01.20-E Rev. 4).
         * NOT used on the normal runtime path — the app always reads the on-device JSON
         * file once it exists. This exists only as (a) the literal content written into
         * that file's very first seed copy is the *bundled asset*, not this constant — this
         * is purely (b) a safety fallback if the on-device file is ever unreadable/corrupt,
         * and (c) a fixture for core's own unit tests, which have no file system/asset
         * access to read from. Keep in sync with the JSON if the AFM data ever changes.
         */
        val DEFAULT = WbConstantsData(
            defaultMtowKg = 770.0,
            maxNonLiftingPartsMassKg = 610.0,
            minUsefulLoadOnSeatsKg = 55.0,
            maxUsefulLoadPerSeatKg = 110.0,
            maxBaggageKg = 12.0,
            fuelDensityKgPerL = 0.75,
            fuelTankCapacityL = FuelTankCapacityL(standard55l = 55.0, longRange79l = 79.0),
            armsMmAftOfDatum = ArmsMmAftOfDatum(
                seatPayload = 143.0,
                fuelTankStandard55l = 727.0,
                fuelTankLongRange79l = 824.0
            ),
            trimWeightsKg = TrimWeightsKg(
                table = listOf(
                    TrimPoint(5.0, 1.7),
                    TrimPoint(10.0, 3.4),
                    TrimPoint(15.0, 5.1)
                )
            )
        )
    }
}

private val wbConstantsJson = Json { ignoreUnknownKeys = true }

fun parseWbConstants(json: String): WbConstantsData =
    wbConstantsJson.decodeFromString(WbConstantsData.serializer(), json)
