package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppNavigation(viewModel: HRTViewModel) {
    val navController = rememberNavController()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isAiEnabled = profile?.isAiEnabled ?: true
    
    val screens = if (isAiEnabled) {
        listOf(
            Screen.Dashboard,
            Screen.Medications,
            Screen.Effects,
            Screen.Chat,
            Screen.Profile
        )
    } else {
        listOf(
            Screen.Dashboard,
            Screen.Medications,
            Screen.Effects,
            Screen.Profile
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Dashboard.route, Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) { DashboardScreen(viewModel) }
            composable(Screen.Medications.route) { MedicationScreen(viewModel) }
            composable(Screen.Effects.route) { ExpectedEffectsScreen(viewModel) }
            composable(Screen.Chat.route) { ChatScreen(viewModel) }
            composable(Screen.Profile.route) { ProfileScreen(viewModel, onSaved = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                }
            }) }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Filled.Home)
    object Medications : Screen("medications", "Meds", Icons.Filled.Medication)
    object Effects : Screen("effects", "Levels", Icons.Filled.ShowChart)
    object Chat : Screen("chat", "Chat", Icons.AutoMirrored.Filled.Chat)
    object Profile : Screen("profile", "Settings", Icons.Filled.Person)
}
