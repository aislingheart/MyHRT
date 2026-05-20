package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Medication
import com.example.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: HRTViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val insight by viewModel.insightState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val bloodTests by viewModel.bloodTests.collectAsStateWithLifecycle()

    var showLogDialog by remember { mutableStateOf(false) }

    val currentEstimate = remember(medications, bloodTests, profile) {
        val profileStartMillis = profile?.startDateMillis
        com.example.data.Pharmacokinetics.calculateConcentration(medications, 0.0, bloodTests, profileStartMillis)
    }

    LaunchedEffect(profile) {
        if (insight == null) {
            viewModel.fetchInsight(profile)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showLogDialog = true }, modifier = Modifier.testTag("add_log_button")) {
                Icon(Icons.Filled.Add, contentDescription = "Log Today")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                profile?.let { p ->
                    val days = ((System.currentTimeMillis() - p.startDateMillis) / (86400000L)).coerceAtLeast(0)
                    Text("Day $days on HRT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Regimen: ${p.regimenType}", style = MaterialTheme.typography.bodyLarge)
                } ?: run {
                    Text("Welcome to Bloom", style = MaterialTheme.typography.headlineMedium)
                }
            }

            item {
                if (medications.isNotEmpty()) {
                    val isFeminizing = profile?.regimenType != "Masculinizing"
                    val primaryHormoneName = if (isFeminizing) "E2 Estimate" else "T Estimate"
                    val primaryHormoneUnit = if (isFeminizing) "pg/mL" else "ng/dL"
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Current Est. Levels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(String.format("%.1f %s", currentEstimate, primaryHormoneUnit), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(16.dp))
                            
                            Text("Quick Actions", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            medications.forEach { med ->
                                val now = System.currentTimeMillis()
                                val takenRecently = (now - med.lastTakenDateMillis) < (4 * 60 * 60 * 1000) // Within 4 hours ago
                                Button(
                                    onClick = {
                                        if (takenRecently) viewModel.undoMedicationTaken(med) else viewModel.markMedicationTaken(med)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (takenRecently) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(if (takenRecently) "Undo ${med.name}" else "Take ${med.name} (${med.dose})")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("What to Expect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(insight ?: "Loading insights...")
                    }
                }
            }

            item {
                Text("Recent Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (logs.isEmpty()) {
                item {
                    Text("No logs yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(logs) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(log.dateMillis)), fontWeight = FontWeight.Bold)
                            if (log.mood.isNotBlank()) Text("Mood: ${log.mood}")
                            if (log.notes.isNotBlank()) Text("Notes: ${log.notes}")
                        }
                    }
                }
            }
        }
    }

    if (showLogDialog) {
        LogDialog(
            onDismiss = { showLogDialog = false },
            onSave = { notes, mood, symptoms ->
                viewModel.logToday(notes, mood, symptoms)
                showLogDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var notes by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    
    val predefinedSymptoms = listOf("Hot flashes", "Fatigue", "Brain fog", "Nausea", "Headache", "Mood swings", "Breast tenderness", "Acne", "Increased libido", "Decreased libido")
    val selectedSymptoms = remember { mutableStateListOf<String>() }
    var symptomMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = mood, onValueChange = { mood = it }, label = { Text("Mood / Overall Feel") }, modifier = Modifier.fillMaxWidth())
                
                ExposedDropdownMenuBox(expanded = symptomMenuExpanded, onExpandedChange = { symptomMenuExpanded = !symptomMenuExpanded }) {
                    OutlinedTextField(
                        value = if (selectedSymptoms.isEmpty()) "Select Symptoms" else selectedSymptoms.joinToString(", "),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = symptomMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Symptoms") }
                    )
                    ExposedDropdownMenu(expanded = symptomMenuExpanded, onDismissRequest = { symptomMenuExpanded = false }) {
                        predefinedSymptoms.forEach { symp ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = selectedSymptoms.contains(symp), onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(symp)
                                    }
                                },
                                onClick = {
                                    if (selectedSymptoms.contains(symp)) selectedSymptoms.remove(symp)
                                    else selectedSymptoms.add(symp)
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Detailed Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(notes, mood, selectedSymptoms.joinToString(", ")) }, modifier = Modifier.testTag("save_log_button")) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
