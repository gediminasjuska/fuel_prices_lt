package com.fuelprices.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fuelprices.data.db.AppDatabase
import com.fuelprices.data.db.FavoriteEntity
import com.fuelprices.data.db.FuelPriceEntity
import com.fuelprices.data.repository.FuelPriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenMode { ALL, FAVORITES, SETTINGS, DETAIL }

data class PriceTrend(
    val petrol95: Int = 0,
    val petrol98: Int = 0,
    val diesel: Int = 0,
    val lpg: Int = 0
)

data class LowestPrices(
    val petrol95: FuelPriceEntity? = null,
    val diesel: FuelPriceEntity? = null,
    val lpg: FuelPriceEntity? = null
)

data class UiState(
    val prices: List<FuelPriceEntity> = emptyList(),
    val companies: List<String> = emptyList(),
    val municipalities: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val favorites: Set<Pair<String, String>> = emptySet(),
    val trends: Map<Pair<String, String>, PriceTrend> = emptyMap(),
    val lowestInFavoriteStation: LowestPrices = LowestPrices(),
    val screenMode: ScreenMode = ScreenMode.ALL,
    val savedCity: String = "",
    val savedStation: String = "",
    val selectedStation: FuelPriceEntity? = null,
    val stationHistory: List<FuelPriceEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdate: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FuelPriceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FuelPriceRepository(
        AppDatabase.getInstance(application)
    )

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _currentDate = MutableStateFlow<String?>(null)
    val companyFilter = MutableStateFlow("")
    val municipalityFilter = MutableStateFlow("")
    val addressFilter = MutableStateFlow("")
    val screenMode = MutableStateFlow(ScreenMode.ALL)
    val savedCity = MutableStateFlow(prefs.getString("saved_city", "") ?: "")
    val savedStation = MutableStateFlow(prefs.getString("saved_station", "") ?: "")

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _trends = MutableStateFlow<Map<Pair<String, String>, PriceTrend>>(emptyMap())
    private val _selectedStation = MutableStateFlow<FuelPriceEntity?>(null)
    private val _stationHistory = MutableStateFlow<List<FuelPriceEntity>>(emptyList())
    private var _previousMode = ScreenMode.ALL

    init {
        val city = savedCity.value
        if (city.isNotEmpty()) municipalityFilter.value = city
        val station = savedStation.value
        if (station.isNotEmpty()) companyFilter.value = station

        // Load data from Supabase
        viewModelScope.launch {
            try {
                _currentDate.value = repository.getLatestDate()
                _isLoading.value = true
                _error.value = null
                val result = repository.refreshPrices(forceDownload = false)
                result.onSuccess { date ->
                    _currentDate.value = date
                }.onFailure { e ->
                    _error.value = e.message ?: "Failed to fetch prices"
                }
                _isLoading.value = false
            } catch (e: Throwable) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to fetch prices"
            }
        }

        // Sync history in background
        viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(1000)
                repository.syncHistory()
                _currentDate.value?.let { loadTrends(it) }
            } catch (_: Throwable) {}
        }
    }

    val uiState: StateFlow<UiState> = combine(
        _currentDate.flatMapLatest { date ->
            if (date != null) repository.getPrices(date) else flowOf(emptyList())
        },
        _currentDate.flatMapLatest { date ->
            if (date != null) repository.getCompanies(date) else flowOf(emptyList())
        },
        _currentDate.flatMapLatest { date ->
            if (date != null) repository.getMunicipalities(date) else flowOf(emptyList())
        },
        combine(_currentDate, municipalityFilter) { date, municipality ->
            Pair(date, municipality)
        }.flatMapLatest { (date, municipality) ->
            if (date != null && municipality.isNotEmpty()) {
                repository.getAddressesByMunicipality(date, municipality)
            } else {
                flowOf(emptyList())
            }
        },
        repository.getAllFavorites(),
        combine(companyFilter, municipalityFilter, addressFilter, screenMode) { c, m, a, s ->
            FilterState(c, m, a, s)
        },
        _isLoading,
        _error,
        combine(_currentDate, savedCity, savedStation, _trends, _selectedStation, _stationHistory) { arr -> arr }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allPrices = values[0] as List<FuelPriceEntity>
        @Suppress("UNCHECKED_CAST")
        val companies = values[1] as List<String>
        @Suppress("UNCHECKED_CAST")
        val municipalities = values[2] as List<String>
        @Suppress("UNCHECKED_CAST")
        val addresses = values[3] as List<String>
        @Suppress("UNCHECKED_CAST")
        val favEntities = values[4] as List<FavoriteEntity>
        val filters = values[5] as FilterState
        val loading = values[6] as Boolean
        val error = values[7] as String?
        @Suppress("UNCHECKED_CAST")
        val extras = values[8] as Array<*>
        val date = extras[0] as String?
        val city = extras[1] as String
        val station = extras[2] as String
        @Suppress("UNCHECKED_CAST")
        val trends = extras[3] as Map<Pair<String, String>, PriceTrend>
        val selStation = extras[4] as FuelPriceEntity?
        @Suppress("UNCHECKED_CAST")
        val history = extras[5] as List<FuelPriceEntity>

        val favSet = favEntities.map { Pair(it.company, it.address) }.toSet()

        val filtered = allPrices.filter { price ->
            val matchesCompany = filters.company.isEmpty() || price.company.equals(filters.company, ignoreCase = true)
            val matchesMunicipality = filters.municipality.isEmpty() || price.municipality.equals(filters.municipality, ignoreCase = true)
            val matchesAddress = filters.address.isEmpty() || price.address.equals(filters.address, ignoreCase = true)
            val matchesFavorite = filters.mode != ScreenMode.FAVORITES || Pair(price.company, price.address) in favSet
            matchesCompany && matchesMunicipality && matchesAddress && matchesFavorite
        }

        val lowestPrices = if (station.isNotEmpty()) {
            val stationPrices = allPrices.filter { p ->
                p.company.equals(station, ignoreCase = true) &&
                        (city.isEmpty() || p.municipality.equals(city, ignoreCase = true))
            }
            LowestPrices(
                petrol95 = stationPrices.filter { it.petrol95 != null }.minByOrNull { it.petrol95!! },
                diesel = stationPrices.filter { it.diesel != null }.minByOrNull { it.diesel!! },
                lpg = stationPrices.filter { it.lpg != null }.minByOrNull { it.lpg!! }
            )
        } else LowestPrices()

        UiState(
            prices = filtered,
            companies = companies,
            municipalities = municipalities,
            addresses = addresses,
            favorites = favSet,
            trends = trends,
            lowestInFavoriteStation = lowestPrices,
            screenMode = filters.mode,
            savedCity = city,
            savedStation = station,
            selectedStation = selStation,
            stationHistory = history,
            isLoading = loading,
            error = error,
            lastUpdate = date
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState(isLoading = true))

    fun refresh(forceDownload: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.refreshPrices(forceDownload = forceDownload)
            result.onSuccess { date ->
                _currentDate.value = date
                loadTrends(date)
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to fetch prices"
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadTrends(currentDate: String) {
        withContext(Dispatchers.IO) {
            val previousPrices = repository.getPreviousDayPrices(currentDate)
            if (previousPrices.isEmpty()) {
                _trends.value = emptyMap()
                return@withContext
            }
            val currentPrices = repository.getPricesListByDate(currentDate)
            val trendMap = mutableMapOf<Pair<String, String>, PriceTrend>()
            for (current in currentPrices) {
                val key = Pair(current.company, current.address)
                val prev = previousPrices[key] ?: continue
                trendMap[key] = PriceTrend(
                    petrol95 = comparePrices(current.petrol95, prev.petrol95),
                    petrol98 = comparePrices(current.petrol98, prev.petrol98),
                    diesel = comparePrices(current.diesel, prev.diesel),
                    lpg = comparePrices(current.lpg, prev.lpg)
                )
            }
            _trends.value = trendMap
        }
    }

    private fun comparePrices(current: Double?, previous: Double?): Int {
        if (current == null || previous == null) return 0
        return when {
            current > previous -> 1
            current < previous -> -1
            else -> 0
        }
    }

    fun openStation(entity: FuelPriceEntity) {
        _selectedStation.value = entity
        _previousMode = screenMode.value
        screenMode.value = ScreenMode.DETAIL
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    repository.getStationHistory(entity.company, entity.address)
                }
                android.util.Log.d("FuelPrices", "History loaded: ${history.size} entries, dates: ${history.map { it.date }}")
                _stationHistory.value = history
            } catch (e: Throwable) {
                android.util.Log.e("FuelPrices", "Failed to load history", e)
            }
        }
    }

    fun closeStation() {
        screenMode.value = _previousMode
        _selectedStation.value = null
        _stationHistory.value = emptyList()
    }

    fun setCompanyFilter(company: String) { companyFilter.value = company }
    fun setMunicipalityFilter(municipality: String) {
        municipalityFilter.value = municipality
        addressFilter.value = ""
    }
    fun setAddressFilter(address: String) { addressFilter.value = address }
    fun setScreenMode(mode: ScreenMode) { screenMode.value = mode }

    fun setSavedCity(city: String) {
        savedCity.value = city
        prefs.edit().putString("saved_city", city).apply()
        municipalityFilter.value = city
        addressFilter.value = ""
    }

    fun setSavedStation(station: String) {
        savedStation.value = station
        prefs.edit().putString("saved_station", station).apply()
        companyFilter.value = station
    }

    fun toggleFavorite(entity: FuelPriceEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entity.company, entity.address)
        }
    }
}

private data class FilterState(
    val company: String,
    val municipality: String,
    val address: String,
    val mode: ScreenMode
)
