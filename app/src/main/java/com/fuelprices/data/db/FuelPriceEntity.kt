package com.fuelprices.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_prices")
data class FuelPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val company: String,
    val address: String,
    val municipality: String,
    val settlement: String,
    val petrol95: Double?,
    val petrol98: Double?,
    val diesel: Double?,
    val lpg: Double?,
    val date: String
)
