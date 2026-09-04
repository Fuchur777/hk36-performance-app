package nl.schellenberg.hk36ttc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.schellenberg.hk36ttc.core.wb.AircraftProfile
import nl.schellenberg.hk36ttc.core.wb.FuelTankType

@Entity(tableName = "aircraft_profiles")
data class AircraftProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registration: String,
    val emptyMassKg: Double,
    val emptyMassCgPositionMm: Double,
    val mtowKg: Double,
    val cgEnvelopeForwardLimitMm: Double,
    val cgEnvelopeAftLimitMm: Double,
    val fuelTankType: FuelTankType,
    /** Manual homescreen position set by long-press-drag reordering — `null` until the pilot
     * drags a row for the first time, so every profile sorts alphabetically by [registration]
     * (the default) until then. Once set, ties are broken by ascending value; a brand-new
     * profile is always `null` at creation, so it lands after every already-positioned one
     * (see [AircraftProfileDao.observeAll]'s `ORDER BY`), alphabetically among the other unset
     * ones. */
    val sortOrder: Long? = null
)

fun AircraftProfileEntity.toDomain(): AircraftProfile = AircraftProfile(
    registration = registration,
    emptyMassKg = emptyMassKg,
    emptyMassCgPositionMm = emptyMassCgPositionMm,
    mtowKg = mtowKg,
    cgEnvelopeForwardLimitMm = cgEnvelopeForwardLimitMm,
    cgEnvelopeAftLimitMm = cgEnvelopeAftLimitMm,
    fuelTankType = fuelTankType
)

fun AircraftProfile.toEntity(id: Long = 0, sortOrder: Long? = null): AircraftProfileEntity = AircraftProfileEntity(
    id = id,
    registration = registration,
    emptyMassKg = emptyMassKg,
    emptyMassCgPositionMm = emptyMassCgPositionMm,
    mtowKg = mtowKg,
    cgEnvelopeForwardLimitMm = cgEnvelopeForwardLimitMm,
    cgEnvelopeAftLimitMm = cgEnvelopeAftLimitMm,
    fuelTankType = fuelTankType,
    sortOrder = sortOrder
)
