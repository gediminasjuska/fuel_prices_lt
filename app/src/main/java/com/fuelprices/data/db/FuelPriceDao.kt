package com.fuelprices.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelPriceDao {

    @Query("SELECT * FROM fuel_prices WHERE date = :date ORDER BY company, address")
    fun getPricesByDate(date: String): Flow<List<FuelPriceEntity>>

    @Query("SELECT DISTINCT company FROM fuel_prices WHERE date = :date ORDER BY company")
    fun getCompanies(date: String): Flow<List<String>>

    @Query("SELECT DISTINCT municipality FROM fuel_prices WHERE date = :date AND municipality != '' ORDER BY municipality")
    fun getMunicipalities(date: String): Flow<List<String>>

    @Query("SELECT DISTINCT address FROM fuel_prices WHERE date = :date AND municipality = :municipality AND address != '' ORDER BY address")
    fun getAddressesByMunicipality(date: String, municipality: String): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM fuel_prices ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDate(): String?

    @Query("SELECT DISTINCT date FROM fuel_prices ORDER BY date DESC")
    suspend fun getAllDates(): List<String>

    @Query("SELECT date FROM fuel_prices WHERE date < :currentDate GROUP BY date ORDER BY date DESC LIMIT 1")
    suspend fun getPreviousDate(currentDate: String): String?

    @Query("SELECT * FROM fuel_prices WHERE date = :date")
    suspend fun getPricesListByDate(date: String): List<FuelPriceEntity>

    @Query("SELECT COUNT(*) FROM fuel_prices WHERE date = :date")
    suspend fun countByDate(date: String): Int

    @Query("DELETE FROM fuel_prices WHERE date NOT IN (:validDates)")
    suspend fun deleteInvalidDates(validDates: List<String>)

    @Query("SELECT * FROM fuel_prices WHERE company = :company AND address = :address GROUP BY date ORDER BY date ASC")
    suspend fun getStationHistory(company: String, address: String): List<FuelPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<FuelPriceEntity>)

    @Query("DELETE FROM fuel_prices WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM fuel_prices WHERE date != :keepDate")
    suspend fun deleteOldPrices(keepDate: String)

    // Favorites
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesSync(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun removeFavorite(favorite: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE company = :company AND address = :address)")
    suspend fun isFavorite(company: String, address: String): Boolean
}
