package com.fuelprices.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelprices.data.db.FuelPriceEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelPriceListScreen(viewModel: FuelPriceViewModel) {
    val state by viewModel.uiState.collectAsState()
    val companyFilter by viewModel.companyFilter.collectAsState()
    val municipalityFilter by viewModel.municipalityFilter.collectAsState()
    val addressFilter by viewModel.addressFilter.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentMode = state.screenMode,
                savedCity = state.savedCity,
                onModeSelected = { mode ->
                    viewModel.setScreenMode(mode)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (state.screenMode) {
                                    ScreenMode.FAVORITES -> "MĖGSTAMOS"
                                    ScreenMode.SETTINGS -> "NUSTATYMAI"
                                    else -> "DEGALŲ KAINOS"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                            state.lastUpdate?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Meniu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atnaujinti")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Thick top border
                    HorizontalDivider(thickness = 4.dp, color = MaterialTheme.colorScheme.outline)

                    val currentStation = state.selectedStation
                    if (state.screenMode == ScreenMode.DETAIL && currentStation != null) {
                        StationDetailScreen(
                            station = currentStation,
                            history = state.stationHistory,
                            trend = state.trends[Pair(currentStation.company, currentStation.address)],
                            onBack = { viewModel.closeStation() }
                        )
                        return@PullToRefreshBox
                    }

                    if (state.screenMode == ScreenMode.SETTINGS) {
                        SettingsContent(
                            municipalities = state.municipalities,
                            savedCity = state.savedCity,
                            companies = state.companies,
                            savedStation = state.savedStation,
                            onCitySelected = { viewModel.setSavedCity(it) },
                            onStationSelected = { viewModel.setSavedStation(it) }
                        )
                        return@PullToRefreshBox
                    }

                    // Collapsible filters
                    var filtersExpanded by remember { mutableStateOf(false) }
                    val hasActiveFilters = companyFilter.isNotEmpty() ||
                            municipalityFilter.isNotEmpty() || addressFilter.isNotEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filtersExpanded = !filtersExpanded }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "FILTRAI",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black
                            )
                            if (hasActiveFilters) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .background(MaterialTheme.colorScheme.outline)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    val count = listOf(companyFilter, municipalityFilter, addressFilter)
                                        .count { it.isNotEmpty() }
                                    Text(
                                        "$count",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Icon(
                            if (filtersExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (filtersExpanded) "Sutraukti" else "Išskleisti"
                        )
                    }

                    AnimatedVisibility(visible = filtersExpanded) {
                        FilterSection(
                            companies = state.companies,
                            selectedCompany = companyFilter,
                            municipalities = state.municipalities,
                            selectedMunicipality = municipalityFilter,
                            addresses = state.addresses,
                            selectedAddress = addressFilter,
                            onCompanySelected = { viewModel.setCompanyFilter(it) },
                            onMunicipalitySelected = { viewModel.setMunicipalityFilter(it) },
                            onAddressSelected = { viewModel.setAddressFilter(it) }
                        )
                    }

                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

                    // Lowest prices in favorite station — always visible
                    if (state.savedStation.isNotEmpty() && state.screenMode != ScreenMode.SETTINGS) {
                        LowestPriceBanner(
                            stationName = state.savedStation,
                            cityName = state.savedCity,
                            lowest = state.lowestInFavoriteStation,
                            trends = state.trends,
                            onClick = { entity -> viewModel.openStation(entity) }
                        )
                        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    }

                    // Error
                    AnimatedVisibility(visible = state.error != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(2.dp, MaterialTheme.colorScheme.outline)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Content
                    when {
                        state.isLoading && state.prices.isEmpty() -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.outline,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "KRAUNAMA...",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        state.prices.isEmpty() && !state.isLoading -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (state.screenMode == ScreenMode.FAVORITES)
                                        "NĖRA MĖGSTAMŲ" else "KAINŲ NERASTA",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.prices, key = { it.id }) { price ->
                                    val key = Pair(price.company, price.address)
                                    FuelPriceCard(
                                        price = price,
                                        isFavorite = key in state.favorites,
                                        trend = state.trends[key],
                                        onToggleFavorite = { viewModel.toggleFavorite(price) },
                                        onClick = { viewModel.openStation(price) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    currentMode: ScreenMode,
    savedCity: String,
    onModeSelected: (ScreenMode) -> Unit
) {
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        unselectedContainerColor = Color.Transparent,
    )

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = MaterialTheme.shapes.medium,
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.outline)
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "MENIU",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (savedCity.isNotEmpty()) {
                    Text(
                        text = savedCity.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // All stations
        NavigationDrawerItem(
            label = {
                Text(
                    "VISOS DEGALINĖS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (currentMode == ScreenMode.ALL) FontWeight.Black else FontWeight.Bold
                )
            },
            selected = currentMode == ScreenMode.ALL,
            onClick = { onModeSelected(ScreenMode.ALL) },
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = drawerItemColors
        )

        // Favorites
        NavigationDrawerItem(
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "MĖGSTAMOS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (currentMode == ScreenMode.FAVORITES) FontWeight.Black else FontWeight.Bold
                    )
                }
            },
            selected = currentMode == ScreenMode.FAVORITES,
            onClick = { onModeSelected(ScreenMode.FAVORITES) },
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = drawerItemColors
        )

        Spacer(Modifier.weight(1f))

        // Settings
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        NavigationDrawerItem(
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "NUSTATYMAI",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (currentMode == ScreenMode.SETTINGS) FontWeight.Black else FontWeight.Bold
                    )
                }
            },
            selected = currentMode == ScreenMode.SETTINGS,
            onClick = { onModeSelected(ScreenMode.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = drawerItemColors
        )

        // Footer
        Text(
            text = "ENA.LT // LEA",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SettingsContent(
    municipalities: List<String>,
    savedCity: String,
    companies: List<String>,
    savedStation: String,
    onCitySelected: (String) -> Unit,
    onStationSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // City setting
        item {
            Text(
                text = "MIESTAS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Savivaldybės filtras bus automatiškai pritaikytas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            BrutalistDropdown(
                label = "SAVIVALDYBĖ",
                placeholder = "NEPASIRINKTA",
                items = municipalities,
                selectedItem = savedCity,
                onItemSelected = onCitySelected
            )
        }

        // Station setting
        item {
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        }

        item {
            Text(
                text = "DEGALINĖ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Pasirinkta degalinė bus automatiškai filtruojama.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            BrutalistDropdown(
                label = "DEGALINIŲ TINKLAS",
                placeholder = "NEPASIRINKTA",
                items = companies,
                selectedItem = savedStation,
                onItemSelected = onStationSelected
            )
        }

        // Active settings summary
        if (savedCity.isNotEmpty() || savedStation.isNotEmpty()) {
            item {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            }
            item {
                Text(
                    text = "AKTYVŪS NUSTATYMAI",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, MaterialTheme.colorScheme.outline)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (savedCity.isNotEmpty()) {
                        Row {
                            Text("MIESTAS: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(savedCity.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                    }
                    if (savedStation.isNotEmpty()) {
                        Row {
                            Text("DEGALINĖ: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(savedStation.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    companies: List<String>,
    selectedCompany: String,
    municipalities: List<String>,
    selectedMunicipality: String,
    addresses: List<String>,
    selectedAddress: String,
    onCompanySelected: (String) -> Unit,
    onMunicipalitySelected: (String) -> Unit,
    onAddressSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrutalistDropdown("DEGALINĖ", "VISOS DEGALINĖS", companies, selectedCompany, onCompanySelected)
        BrutalistDropdown("SAVIVALDYBĖ", "VISOS SAVIVALDYBĖS", municipalities, selectedMunicipality, onMunicipalitySelected)
        BrutalistDropdown(
            "ADRESAS",
            if (selectedMunicipality.isEmpty()) "PIRMA PASIRINKITE SAVIVALDYBĘ" else "VISI ADRESAI",
            addresses,
            selectedAddress,
            onAddressSelected,
            enabled = selectedMunicipality.isNotEmpty()
        )
    }
}

@Composable
private fun BrutalistDropdown(
    label: String,
    placeholder: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = selectedItem.ifEmpty { placeholder },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            singleLine = true
        )
        // Transparent click overlay — OutlinedTextField consumes clicks, so we overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        placeholder,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    onItemSelected("")
                    expanded = false
                }
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LowestPriceBanner(
    stationName: String,
    cityName: String,
    lowest: LowestPrices,
    trends: Map<Pair<String, String>, PriceTrend>,
    onClick: (FuelPriceEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = buildString {
                append("PIGIAUSIA · ${stationName.uppercase()}")
                if (cityName.isNotEmpty()) append(" · ${cityName.uppercase()}")
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            lowest.petrol95?.let { entity ->
                val trend = trends[Pair(entity.company, entity.address)]
                LowestPriceChip(
                    tag = "95", bgColor = 0xFF2E7D32,
                    price = entity.petrol95!!, address = entity.address,
                    trendDir = trend?.petrol95 ?: 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onClick(entity) }
                )
            }
            lowest.diesel?.let { entity ->
                val trend = trends[Pair(entity.company, entity.address)]
                LowestPriceChip(
                    tag = "DYZ", bgColor = 0xFF000000,
                    price = entity.diesel!!, address = entity.address,
                    trendDir = trend?.diesel ?: 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onClick(entity) }
                )
            }
            lowest.lpg?.let { entity ->
                val trend = trends[Pair(entity.company, entity.address)]
                LowestPriceChip(
                    tag = "SND", bgColor = 0xFFD84315,
                    price = entity.lpg!!, address = entity.address,
                    trendDir = trend?.lpg ?: 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onClick(entity) }
                )
            }
        }
    }
}

@Composable
private fun LowestPriceChip(
    tag: String,
    bgColor: Long,
    price: Double,
    address: String,
    trendDir: Int = 0,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .border(2.dp, Color(bgColor))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(Color(bgColor))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                tag,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                String.format("%.3f", price),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            if (trendDir != 0) {
                Text(
                    if (trendDir > 0) "▲" else "▼",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (trendDir > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Text(
            address.uppercase().take(20),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FuelPriceCard(
    price: FuelPriceEntity,
    isFavorite: Boolean,
    trend: PriceTrend?,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        // Company + favorite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = price.company.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Pašalinti" else "Pridėti",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Address
        if (price.address.isNotBlank() || price.municipality.isNotBlank()) {
            Text(
                text = buildString {
                    if (price.address.isNotBlank()) append(price.address.uppercase())
                    if (price.municipality.isNotBlank()) {
                        if (isNotEmpty()) append(" // ")
                        append(price.municipality.uppercase())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        // Prices row — only show fuel types that have values
        data class FuelItem(val type: FuelType, val price: Double, val trendDir: Int)
        val fuelItems = listOfNotNull(
            price.petrol95?.let { FuelItem(FuelType.PETROL_95, it, trend?.petrol95 ?: 0) },
            price.petrol98?.let { FuelItem(FuelType.PETROL_98, it, trend?.petrol98 ?: 0) },
            price.diesel?.let { FuelItem(FuelType.DIESEL, it, trend?.diesel ?: 0) },
            price.lpg?.let { FuelItem(FuelType.LPG, it, trend?.lpg ?: 0) },
        )

        if (fuelItems.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                fuelItems.forEach { item ->
                    FuelPriceBadge(type = item.type, price = item.price, trendDirection = item.trendDir)
                }
            }
        }
    }
}

private enum class FuelType(
    val tag: String,
    val bgColor: Long,
    val fgColor: Long
) {
    PETROL_95("95", 0xFF2E7D32, 0xFFFFFFFF),   // green
    PETROL_98("98", 0xFF1565C0, 0xFFFFFFFF),   // blue
    DIESEL("DYZ", 0xFF000000, 0xFFFFFFFF),     // black bg, white text
    LPG("SND", 0xFFD84315, 0xFFFFFFFF),        // red-orange
}

@Composable
private fun FuelPriceBadge(type: FuelType, price: Double, trendDirection: Int = 0) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Colored logo badge
        Box(
            modifier = Modifier
                .background(Color(type.bgColor))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = type.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color(type.fgColor),
                letterSpacing = 0.5.sp
            )
        }
        // Price value
        Text(
            text = String.format("%.3f", price),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        // Trend arrow
        if (trendDirection != 0) {
            Text(
                text = if (trendDirection > 0) "▲" else "▼",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (trendDirection > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
            )
        }
    }
}
