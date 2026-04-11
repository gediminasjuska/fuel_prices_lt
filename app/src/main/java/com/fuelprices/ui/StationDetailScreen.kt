package com.fuelprices.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelprices.data.db.FuelPriceEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class FuelTab(
    val label: String,
    val tag: String,
    val bgColor: Long,
    val fgColor: Long,
    val lineColor: Long
) {
    PETROL_95("95 BENZINAS", "95", 0xFF2E7D32, 0xFFFFFFFF, 0xFF2E7D32),
    DIESEL("DYZELIS", "DYZ", 0xFF000000, 0xFFFFFFFF, 0xFF000000),
    LPG("SND", "SND", 0xFFD84315, 0xFFFFFFFF, 0xFFD84315)
}

private enum class PeriodTab(val label: String, val days: Int) {
    WEEK("7 D.", 7),
    MONTH("MĖN.", 30),
    ALL("VISAS", Int.MAX_VALUE)
}

@Composable
fun StationDetailScreen(
    station: FuelPriceEntity,
    history: List<FuelPriceEntity>,
    trend: PriceTrend?,
    onBack: () -> Unit
) {
    var selectedFuel by remember { mutableStateOf(
        when {
            station.petrol95 != null -> FuelTab.PETROL_95
            station.diesel != null -> FuelTab.DIESEL
            station.lpg != null -> FuelTab.LPG
            else -> FuelTab.PETROL_95
        }
    )}
    var selectedPeriod by remember { mutableStateOf(PeriodTab.WEEK) }

    val currentPrice = when (selectedFuel) {
        FuelTab.PETROL_95 -> station.petrol95
        FuelTab.DIESEL -> station.diesel
        FuelTab.LPG -> station.lpg
    }

    val trendDir = when (selectedFuel) {
        FuelTab.PETROL_95 -> trend?.petrol95 ?: 0
        FuelTab.DIESEL -> trend?.diesel ?: 0
        FuelTab.LPG -> trend?.lpg ?: 0
    }

    // Filter history by period
    val filteredHistory = remember(history, selectedPeriod) {
        if (selectedPeriod == PeriodTab.ALL || history.size <= 2) {
            history
        } else {
            val cutoff = LocalDate.now().minusDays(selectedPeriod.days.toLong())
            history.filter {
                try {
                    LocalDate.parse(it.date) >= cutoff
                } catch (_: Exception) { true }
            }
        }
    }

    // Extract price series for selected fuel, deduplicated by date
    val chartData = remember(filteredHistory, selectedFuel) {
        filteredHistory.mapNotNull { entry ->
            val price = when (selectedFuel) {
                FuelTab.PETROL_95 -> entry.petrol95
                FuelTab.DIESEL -> entry.diesel
                FuelTab.LPG -> entry.lpg
            }
            price?.let { entry.date to it }
        }.distinctBy { it.first } // keep one entry per date
    }

    // Available fuel tabs (only show fuels this station sells)
    val availableTabs = remember(station) {
        listOfNotNull(
            if (station.petrol95 != null) FuelTab.PETROL_95 else null,
            if (station.diesel != null) FuelTab.DIESEL else null,
            if (station.lpg != null) FuelTab.LPG else null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Back bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "←",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "ATGAL",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
        HorizontalDivider(thickness = 4.dp, color = MaterialTheme.colorScheme.outline)

        // Station header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = station.company.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = buildString {
                    if (station.address.isNotBlank()) append(station.address.uppercase())
                    if (station.municipality.isNotBlank()) {
                        if (isNotEmpty()) append(" // ")
                        append(station.municipality.uppercase())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        // Fuel type tabs
        Row(modifier = Modifier.fillMaxWidth()) {
            availableTabs.forEachIndexed { index, tab ->
                val isSelected = tab == selectedFuel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) Color(tab.bgColor) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selectedFuel = tab }
                        .then(
                            if (index < availableTabs.size - 1)
                                Modifier.border(width = 0.dp, color = Color.Transparent)
                            else Modifier
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(if (isSelected) Color(tab.fgColor) else Color(tab.bgColor))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                tab.tag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) Color(tab.bgColor) else Color(tab.fgColor)
                            )
                        }
                        Text(
                            tab.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(tab.fgColor) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        // Hero price
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPrice != null) {
                Text(
                    text = String.format("%.3f", currentPrice),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-2).sp
                )
                if (trendDir != 0 && chartData.size >= 2) {
                    val prevPrice = chartData[chartData.size - 2].second
                    val diff = currentPrice - prevPrice
                    if (kotlin.math.abs(diff) > 0.0005) {
                        Text(
                            text = "${if (diff > 0) "▲" else "▼"} ${String.format("%.3f", kotlin.math.abs(diff))} EUR/L",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (diff > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                        )
                    }
                }
                Text(
                    text = "EUR / LITRAS · ${station.date}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    "NEPREKIAUJA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        // Chart section
        if (chartData.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "KAINŲ POKYTIS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PeriodTab.entries.forEach { period ->
                            val isActive = period == selectedPeriod
                            Box(
                                modifier = Modifier
                                    .background(if (isActive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedPeriod = period }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    period.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                PriceChart(
                    data = chartData,
                    lineColor = Color(selectedFuel.lineColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline)
                        .padding(8.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "NEPAKANKA DUOMENŲ GRAFIKUI",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriceChart(
    data: List<Pair<String, Double>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    var selectedIndex by remember(data) { mutableIntStateOf(-1) }
    val density = LocalDensity.current

    // Compose-based layout: Y-labels on left, chart area on right, X-labels below
    Column(modifier = modifier) {
        // Selected point info bar
        if (selectedIndex in data.indices) {
            val (date, price) = data[selectedIndex]
            val shortDate = if (date.length >= 10) date.substring(5) else date
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "$shortDate  ·  €${String.format("%.3f", price)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }

        // Chart canvas
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Y-axis labels column
            val prices = data.map { it.second }
            val minP = prices.min()
            val maxP = prices.max()
            val pad = if (maxP - minP < 0.001) 0.005 else (maxP - minP) * 0.15
            val yMin = minP - pad
            val yMax = maxP + pad
            val gridSteps = 4

            Column(
                modifier = Modifier
                    .width(62.dp)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 0..gridSteps) {
                    val price = yMax - (i.toDouble() / gridSteps) * (yMax - yMin)
                    Text(
                        String.format("%.3f", price),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF888888)
                    )
                }
            }

            // Canvas for the actual chart
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(data) {
                        detectTapGestures { tapOffset ->
                            val cw = size.width.toFloat()
                            val ch = size.height.toFloat()
                            val padTop = 12f
                            val padBottom = 12f
                            val drawH = ch - padTop - padBottom

                            var nearest = -1
                            var nearestDist = Float.MAX_VALUE

                            data.forEachIndexed { i, (_, price) ->
                                val x = if (data.size == 1) cw / 2
                                else (i.toFloat() / (data.size - 1)) * cw
                                val y = padTop + (1f - ((price - yMin) / (yMax - yMin)).toFloat()) * drawH
                                val dist = kotlin.math.sqrt(
                                    (tapOffset.x - x) * (tapOffset.x - x) +
                                            (tapOffset.y - y) * (tapOffset.y - y)
                                )
                                if (dist < nearestDist) {
                                    nearestDist = dist
                                    nearest = i
                                }
                            }
                            selectedIndex = if (nearest == selectedIndex) -1 else nearest
                        }
                    }
            ) {
                val cw = size.width
                val ch = size.height
                val padTop = 12f
                val padBottom = 12f
                val drawH = ch - padTop - padBottom

                // Grid lines
                for (i in 0..gridSteps) {
                    val gy = padTop + (i.toFloat() / gridSteps) * drawH
                    drawLine(
                        Color(0xFFE0E0E0), Offset(0f, gy), Offset(cw, gy), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                }

                // Calculate points
                val pts = data.mapIndexed { i, (_, price) ->
                    val x = if (data.size == 1) cw / 2
                    else (i.toFloat() / (data.size - 1)) * cw
                    val y = padTop + (1f - ((price - yMin) / (yMax - yMin)).toFloat()) * drawH
                    Offset(x, y)
                }

                // Area fill
                val path = android.graphics.Path()
                pts.forEachIndexed { i, pt ->
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                path.lineTo(pts.last().x, padTop + drawH)
                path.lineTo(pts.first().x, padTop + drawH)
                path.close()
                drawContext.canvas.nativeCanvas.drawPath(path, android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(20,
                        (lineColor.red * 255).toInt(),
                        (lineColor.green * 255).toInt(),
                        (lineColor.blue * 255).toInt())
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                })

                // Line
                for (i in 0 until pts.size - 1) {
                    drawLine(lineColor, pts[i], pts[i + 1], strokeWidth = 4f)
                }

                // Dots
                pts.forEachIndexed { i, pt ->
                    val isSelected = i == selectedIndex
                    val outerR = if (isSelected) 14f else 10f
                    val innerR = if (isSelected) 10f else 7f
                    drawCircle(Color.White, outerR, pt)
                    drawCircle(lineColor, innerR, pt)
                }
            }
        }

        // X-axis date labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 62.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { (date, _) ->
                val shortDate = if (date.length >= 10) date.substring(5) else date
                Text(
                    shortDate,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFAAAAAA)
                )
            }
        }
    }
}
