package com.fuelprices.data.db

import androidx.room.Entity

@Entity(tableName = "favorites", primaryKeys = ["company", "address"])
data class FavoriteEntity(
    val company: String,
    val address: String
)
