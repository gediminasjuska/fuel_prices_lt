package com.fuelprices

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fuelprices.worker.FuelPriceFetchWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class FuelPricesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleDailyFetch()
    }

    private fun scheduleDailyFetch() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calculate delay until next 10:30 AM
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(10, 30)
        var nextRun = now.toLocalDate().atTime(targetTime)
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1)
        }
        val delayMinutes = Duration.between(now, nextRun).toMinutes()

        val workRequest = PeriodicWorkRequestBuilder<FuelPriceFetchWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FuelPriceFetchWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
