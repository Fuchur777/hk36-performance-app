package nl.schellenberg.hk36ttc.data.local

import androidx.room.TypeConverter
import nl.schellenberg.hk36ttc.core.wb.FuelTankType

class Converters {
    @TypeConverter
    fun fromFuelTankType(value: FuelTankType): String = value.name

    @TypeConverter
    fun toFuelTankType(value: String): FuelTankType = FuelTankType.valueOf(value)
}
