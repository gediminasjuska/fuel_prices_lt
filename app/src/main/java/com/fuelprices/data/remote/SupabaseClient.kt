package com.fuelprices.data.remote

import com.fuelprices.data.db.FuelPriceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseClient {

    companion object {
        private const val BASE_URL = "https://iwkcjhmiclubpysgmtai.supabase.co/rest/v1"
        private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml3a2NqaG1pY2x1YnB5c2dtdGFpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU4NTk3MTMsImV4cCI6MjA5MTQzNTcxM30.4bLrZDUrb7zZLqqLtqhSBRSsJ2W_lLRYMEsaSE6wiSs"
    }

    suspend fun fetchLatestDate(): String? = withContext(Dispatchers.IO) {
        val json = httpGet("$BASE_URL/fuel_prices?select=date&order=date.desc&limit=1")
        val arr = JSONArray(json)
        if (arr.length() > 0) arr.getJSONObject(0).getString("date") else null
    }

    suspend fun fetchAllDates(): List<String> = withContext(Dispatchers.IO) {
        // Use RPC or distinct query — Supabase REST doesn't have DISTINCT,
        // so we select date, group implicitly by ordering
        val json = httpGet("$BASE_URL/fuel_prices?select=date&order=date.desc")
        val arr = JSONArray(json)
        val dates = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            dates.add(arr.getJSONObject(i).getString("date"))
        }
        dates.sorted().toList()
    }

    suspend fun fetchPrices(date: String): List<FuelPriceEntity> = withContext(Dispatchers.IO) {
        val encodedDate = URLEncoder.encode(date, "UTF-8")
        val json = httpGet("$BASE_URL/fuel_prices?date=eq.$encodedDate&order=company,address&limit=2000")
        parseEntities(json)
    }

    suspend fun fetchAllPrices(): List<FuelPriceEntity> = withContext(Dispatchers.IO) {
        // Fetch all records (paginate if needed)
        val allEntities = mutableListOf<FuelPriceEntity>()
        var offset = 0
        val pageSize = 1000

        while (true) {
            val json = httpGet("$BASE_URL/fuel_prices?order=date.desc,company,address&limit=$pageSize&offset=$offset")
            val batch = parseEntities(json)
            if (batch.isEmpty()) break
            allEntities.addAll(batch)
            if (batch.size < pageSize) break
            offset += pageSize
        }

        allEntities
    }

    private fun parseEntities(json: String): List<FuelPriceEntity> {
        val arr = JSONArray(json)
        val entities = mutableListOf<FuelPriceEntity>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            entities.add(
                FuelPriceEntity(
                    company = obj.optString("company", ""),
                    address = obj.optString("address", ""),
                    municipality = obj.optString("municipality", ""),
                    settlement = "",
                    petrol95 = obj.optDoubleOrNull("petrol95"),
                    petrol98 = obj.optDoubleOrNull("petrol98"),
                    diesel = obj.optDoubleOrNull("diesel"),
                    lpg = obj.optDoubleOrNull("lpg"),
                    date = obj.optString("date", "")
                )
            )
        }

        return entities
    }

    private fun org.json.JSONObject.optDoubleOrNull(key: String): Double? {
        if (isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN() || v <= 0) null else v
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("apikey", API_KEY)
        connection.setRequestProperty("Authorization", "Bearer $API_KEY")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 30000

        try {
            val code = connection.responseCode
            if (code != 200) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Supabase API error $code: $error")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
