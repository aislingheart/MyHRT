package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.HRTRepository
import com.example.ui.AppNavigation
import com.example.ui.HRTViewModel
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "hrt-database"
        ).fallbackToDestructiveMigration().build()
        val repository = HRTRepository(database.hrtDao())
        val viewModel = HRTViewModel(repository)

        enableEdgeToEdge()
        setContent {
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDark = profile?.isDarkTheme ?: isSystemDark
            val useDynamic = profile?.useDynamicColor ?: true
            
            MyApplicationTheme(darkTheme = useDark, dynamicColor = useDynamic) {
                if (profile == null) {
                    com.example.ui.OnboardingScreen(viewModel, onComplete = {
                        // Will trigger recomposition and show AppNavigation once profile is non-null
                    })
                } else {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}
