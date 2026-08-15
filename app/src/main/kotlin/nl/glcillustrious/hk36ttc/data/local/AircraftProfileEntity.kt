package nl.glcillustrious.hk36ttc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.glcillustrious.hk36ttc.core.wb.AircraftProfile
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType

@Entity(tableName = "aircraft_profiles")
data class AircraftProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registration: String,
    val emptyMassKg: Double,
    val emptyMassCgPositionMm: Double,
    val mtowKg: Double,
    val cgEnvelopeForwardLimitMm: Double,
    val cgEnvelopeAftLimitMm: Double,
    val fuelTankType: FuelTankType
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

fun AircraftProfile.toEntity(id: Long = 0): AircraftProfileEntity = AircraftProfileEntity(
    id = id,
    registration = registration,
    emptyMassKg = emptyMassKg,
    emptyMassCgPositionMm = emptyMassCgPositionMm,
    mtowKg = mtowKg,
    cgEnvelopeForwardLimitMm = cgEnvelopeForwardLimitMm,
    cgEnvelopeAftLimitMm = cgEnvelopeAftLimitMm,
    fuelTankType = fuelTankType
)
