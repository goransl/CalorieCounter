package com.example.floating.caloriecounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.ui.theme.CalorieCounterTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: FoodRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FoodRepository()

        setContent {
            CalorieCounterTheme {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color(0xFF121212).toArgb()
                WindowInsetsControllerCompat(window, window.decorView)
                    .isAppearanceLightStatusBars = false

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(repository)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.close()
    }
}
