package com.sethdigital.caloriepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sethdigital.caloriepro.ui.AppNavHost
import com.sethdigital.caloriepro.ui.theme.CalorieProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalorieProTheme {
                AppNavHost()
            }
        }
    }
}
