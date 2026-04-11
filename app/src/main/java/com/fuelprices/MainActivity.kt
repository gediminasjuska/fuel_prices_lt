package com.fuelprices

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelprices.ui.FuelPriceListScreen
import com.fuelprices.ui.FuelPriceViewModel
import com.fuelprices.ui.theme.FuelPricesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FuelPricesTheme {
                val viewModel: FuelPriceViewModel = viewModel()
                FuelPriceListScreen(viewModel)
            }
        }
    }
}
