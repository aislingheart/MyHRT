package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.AppDatabase
import com.example.data.HRTRepository
import com.example.ui.AppNavigation
import com.example.ui.HRTViewModel
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var startupError: String? = null
        var database: AppDatabase? = null
        var repository: HRTRepository? = null
        var viewModel: HRTViewModel? = null

        try {
            database = AppDatabase.getInstance(applicationContext)
            repository = HRTRepository(database.hrtDao())
            viewModel = HRTViewModel(repository)
        } catch (e: Throwable) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            startupError = sw.toString()
        }

        setContent {
            var errorText by remember { mutableStateOf(startupError) }

            if (errorText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "App crashed on startup:\n\n$errorText",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val profile by viewModel!!.userProfile.collectAsStateWithLifecycle()
                val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val useDark = profile?.isDarkTheme ?: isSystemDark
                val useDynamic = profile?.useDynamicColor ?: true

                MyApplicationTheme(darkTheme = useDark, dynamicColor = useDynamic) {
                    if (profile == null) {
                        com.example.ui.OnboardingScreen(viewModel!!, onComplete = {
                            // Will trigger recomposition and show AppNavigation once profile is non-null
                        })
                    } else {
                        AppNavigation(viewModel!!)
                    }
                }
            }
        }
    }
}
