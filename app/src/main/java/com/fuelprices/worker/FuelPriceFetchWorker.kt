package com.fuelprices.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fuelprices.data.db.AppDatabase
import com.fuelprices.data.repository.FuelPriceRepository
import com.fuelprices.widget.FuelPriceWidget

class FuelPriceFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FuelPriceRepository(
            AppDatabase.getInstance(applicationContext)
        )

        return try {
            val result = repository.refreshPrices()
            if (result.isSuccess) {
                // Also sync any missing historical data
                repository.syncHistory()
                // Update widget
                FuelPriceWidget.triggerUpdate(applicationContext)
                Result.success()
            } else if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (_: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "fuel_price_fetch"
    }
}
