package com.fuelprices.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.fuelprices.MainActivity
import com.fuelprices.R
import com.fuelprices.data.db.AppDatabase
import com.fuelprices.data.db.FuelPriceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FuelPriceWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FuelPriceWidget::class.java))
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.fuelprices.widget.REFRESH"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, FuelPriceWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val views = buildWidgetViews(context)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (_: Throwable) {
                    // Silent fail for widget
                }
            }
        }

        private suspend fun buildWidgetViews(context: Context): RemoteViews =
            withContext(Dispatchers.IO) {
                val views = RemoteViews(context.packageName, R.layout.widget_fuel_prices)
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val savedStation = prefs.getString("saved_station", "") ?: ""
                val savedCity = prefs.getString("saved_city", "") ?: ""

                val db = AppDatabase.getInstance(context)
                val dao = db.fuelPriceDao()

                val latestDate = dao.getLatestDate() ?: run {
                    views.setTextViewText(R.id.widget_station_name, "NĖRA DUOMENŲ")
                    return@withContext views
                }

                val shortDate = if (latestDate.length >= 10) latestDate.substring(5) else latestDate
                views.setTextViewText(R.id.widget_date, shortDate)

                // Get all prices for today
                val allPrices = dao.getPricesListByDate(latestDate)

                // Get previous day prices for trend
                val prevDate = dao.getPreviousDate(latestDate)
                val prevPrices = if (prevDate != null) {
                    dao.getPricesListByDate(prevDate)
                        .associateBy { Pair(it.company, it.address) }
                } else emptyMap()

                // Filter by saved station and city
                val stationPrices = if (savedStation.isNotEmpty()) {
                    allPrices.filter { p ->
                        p.company.equals(savedStation, ignoreCase = true) &&
                                (savedCity.isEmpty() || p.municipality.equals(savedCity, ignoreCase = true))
                    }
                } else allPrices

                val headerText = if (savedStation.isNotEmpty()) {
                    "${savedStation.uppercase()} · PIGIAUSIA · $shortDate"
                } else "DEGALŲ KAINOS · $shortDate"
                views.setTextViewText(R.id.widget_station_name, headerText)

                // Find lowest prices
                val lowest95 = stationPrices.filter { it.petrol95 != null }.minByOrNull { it.petrol95!! }
                val lowestDyz = stationPrices.filter { it.diesel != null }.minByOrNull { it.diesel!! }
                val lowestSnd = stationPrices.filter { it.lpg != null }.minByOrNull { it.lpg!! }

                // Set 95 cell
                setPriceCell(views, R.id.widget_95_price, R.id.widget_95_arrow, R.id.widget_95_addr,
                    lowest95, lowest95?.petrol95, prevPrices, "petrol95")

                // Set DYZ cell
                setPriceCell(views, R.id.widget_dyz_price, R.id.widget_dyz_arrow, R.id.widget_dyz_addr,
                    lowestDyz, lowestDyz?.diesel, prevPrices, "diesel")

                // Set SND cell
                setPriceCell(views, R.id.widget_snd_price, R.id.widget_snd_arrow, R.id.widget_snd_addr,
                    lowestSnd, lowestSnd?.lpg, prevPrices, "lpg")

                // Favorite stations row
                val favorites = dao.getAllFavoritesSync()
                if (favorites.isNotEmpty()) {
                    // Show first favorite station
                    val firstFav = favorites.first()
                    val favPrice = allPrices.find {
                        it.company == firstFav.company && it.address == firstFav.address
                    }
                    if (favPrice != null) {
                        views.setViewVisibility(R.id.widget_fav_row, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_fav_divider, View.VISIBLE)
                        views.setTextViewText(R.id.widget_fav_addr, favPrice.address.uppercase())
                        views.setTextViewText(R.id.widget_fav_95,
                            favPrice.petrol95?.let { String.format("%.3f", it) } ?: "—")
                        views.setTextViewText(R.id.widget_fav_dyz,
                            favPrice.diesel?.let { String.format("%.3f", it) } ?: "—")
                        views.setTextViewText(R.id.widget_fav_snd,
                            favPrice.lpg?.let { String.format("%.3f", it) } ?: "—")
                    }
                }

                // Click opens app
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                views
            }

        private fun setPriceCell(
            views: RemoteViews,
            priceId: Int, arrowId: Int, addrId: Int,
            entity: FuelPriceEntity?,
            price: Double?,
            prevPrices: Map<Pair<String, String>, FuelPriceEntity>,
            fuelField: String
        ) {
            if (entity == null || price == null) {
                views.setTextViewText(priceId, "—")
                views.setTextViewText(arrowId, "")
                views.setTextViewText(addrId, "")
                return
            }

            views.setTextViewText(priceId, String.format("%.3f", price))
            views.setTextViewText(addrId, entity.address.take(18))

            // Trend arrow
            val prev = prevPrices[Pair(entity.company, entity.address)]
            val prevPrice = when (fuelField) {
                "petrol95" -> prev?.petrol95
                "diesel" -> prev?.diesel
                "lpg" -> prev?.lpg
                else -> null
            }

            if (prevPrice != null && prevPrice != price) {
                if (price > prevPrice) {
                    views.setTextViewText(arrowId, "▲")
                    views.setTextColor(arrowId, Color.parseColor("#D32F2F"))
                } else {
                    views.setTextViewText(arrowId, "▼")
                    views.setTextColor(arrowId, Color.parseColor("#2E7D32"))
                }
            } else {
                views.setTextViewText(arrowId, "")
            }
        }
    }
}
