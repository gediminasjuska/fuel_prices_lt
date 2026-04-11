package com.fuelprices.data.repository

import com.fuelprices.data.db.AppDatabase
import com.fuelprices.data.db.FavoriteEntity
import com.fuelprices.data.db.FuelPriceEntity
import com.fuelprices.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FuelPriceRepository(
    private val database: AppDatabase,
    private val supabase: SupabaseClient = SupabaseClient()
) {
    private val dao = database.fuelPriceDao()

    fun getPrices(date: String): Flow<List<FuelPriceEntity>> = dao.getPricesByDate(date)

    fun getCompanies(date: String): Flow<List<String>> = dao.getCompanies(date)

    fun getMunicipalities(date: String): Flow<List<String>> = dao.getMunicipalities(date)

    fun getAddressesByMunicipality(date: String, municipality: String): Flow<List<String>> =
        dao.getAddressesByMunicipality(date, municipality)

    suspend fun getLatestDate(): String? = dao.getLatestDate()

    suspend fun getAllDates(): List<String> = dao.getAllDates()

    suspend fun getPricesListByDate(date: String): List<FuelPriceEntity> =
        dao.getPricesListByDate(date)

    fun getAllFavorites(): Flow<List<FavoriteEntity>> = dao.getAllFavorites()

    suspend fun toggleFavorite(company: String, address: String) {
        if (dao.isFavorite(company, address)) {
            dao.removeFavorite(FavoriteEntity(company, address))
        } else {
            dao.addFavorite(FavoriteEntity(company, address))
        }
    }

    suspend fun getStationHistory(company: String, address: String): List<FuelPriceEntity> =
        dao.getStationHistory(company, address)

    suspend fun getPreviousDayPrices(currentDate: String): Map<Pair<String, String>, FuelPriceEntity> =
        withContext(Dispatchers.IO) {
            val previousDate = dao.getPreviousDate(currentDate) ?: return@withContext emptyMap()
            dao.getPricesListByDate(previousDate)
                .associateBy { Pair(it.company, it.address) }
        }

    /**
     * Fetch latest prices from Supabase and cache locally.
     */
    suspend fun refreshPrices(forceDownload: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        try {
            val latestDate = supabase.fetchLatestDate()
                ?: return@withContext Result.failure(Exception("No data available"))

            // Skip if already cached
            if (!forceDownload && dao.countByDate(latestDate) > 0) {
                return@withContext Result.success(latestDate)
            }

            val prices = supabase.fetchPrices(latestDate)
            if (prices.isEmpty()) {
                return@withContext Result.failure(Exception("No prices found"))
            }

            dao.deleteByDate(latestDate)
            dao.insertAll(prices)

            Result.success(latestDate)
        } catch (e: Throwable) {
            Result.failure(Exception(e.message, e))
        }
    }

    /**
     * Sync all historical data from Supabase that we don't have locally.
     */
    suspend fun syncHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteDates = supabase.fetchAllDates()
            val localDates = dao.getAllDates().toSet()

            val missingDates = remoteDates.filter { it !in localDates }
            if (missingDates.isEmpty()) return@withContext Result.success(Unit)

            for (date in missingDates) {
                val prices = supabase.fetchPrices(date)
                if (prices.isNotEmpty()) {
                    dao.deleteByDate(date)
                    dao.insertAll(prices)
                }
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(Exception(e.message, e))
        }
    }
}
