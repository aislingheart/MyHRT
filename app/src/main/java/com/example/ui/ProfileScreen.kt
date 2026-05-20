package com.example.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: HRTViewModel, onSaved: () -> Unit) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val bloodTests by viewModel.bloodTests.collectAsStateWithLifecycle()
    var regimen by remember(profile) { mutableStateOf(profile?.regimenType ?: "Feminizing") }
    
    val systemDark = isSystemInDarkTheme()
    var isDarkTheme by remember(profile) { mutableStateOf(profile?.isDarkTheme ?: systemDark) }
    var useDynamic by remember(profile) { mutableStateOf(profile?.useDynamicColor ?: true) }
    
    var useOnDeviceAi by remember(profile) { mutableStateOf(profile?.useOnDeviceAi ?: false) }
    var apiKey by remember(profile) { mutableStateOf(profile?.apiKey ?: "") }
    
    // Add start date integration
    var startDateMillis by remember(profile) { mutableStateOf(profile?.startDateMillis ?: System.currentTimeMillis()) }
    val formatter = remember { java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val options = listOf("Feminizing", "Masculinizing")

    var showBloodTestDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Your Settings", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                
                Text("Regimen Type & Duration", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            options.forEach { opt ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = (regimen == opt),
                                        onClick = { regimen = opt }
                                    )
                                    Text(opt, modifier = Modifier.padding(end = 16.dp))
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Current Regimen Start Date: ${formatter.format(java.util.Date(startDateMillis))}")
                        }
                    }
                }
                
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                startDateMillis = datePickerState.selectedDateMillis ?: startDateMillis
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dark Mode")
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { isDarkTheme = it; viewModel.setTheme(it, useDynamic) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Use Monet Theme\n(Dynamic Colors)")
                            Switch(
                                checked = useDynamic,
                                onCheckedChange = { useDynamic = it; viewModel.setTheme(isDarkTheme, it) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Use On-Device AI (Gemini Nano)\nRequires AICore support.")
                            Switch(
                                checked = useOnDeviceAi,
                                onCheckedChange = { useOnDeviceAi = it }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        val scope = rememberCoroutineScope()
                        var aiTestResult by remember { mutableStateOf<String?>(null) }
                        Button(onClick = {
                            scope.launch {
                                aiTestResult = "Testing..."
                                try {
                                    aiTestResult = com.example.api.GeminiClient.generateInsight(
                                        "Please reply with exactly 'API Key is working!'",
                                        "You are a helpful tester.",
                                        useOnDeviceAi,
                                        apiKey
                                    )
                                } catch (e: Exception) {
                                    aiTestResult = "Error: ${e.message}"
                                }
                            }
                        }) {
                            Text("Test AI Configuration")
                        }
                        if (aiTestResult != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(aiTestResult!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text("Blood Test Results", style = MaterialTheme.typography.titleMedium)
                Text("Logging tests calibrates the simulation.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            items(bloodTests) { test ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(test.dateMillis))
                        Column {
                            Text(dateStr, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("E2: ${test.e2Level ?: "N/A"} pg/mL, T: ${test.tLevel ?: "N/A"} ng/dL", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { viewModel.removeBloodTest(test.id) }) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showBloodTestDialog = true }) {
                    Text("+ Log Blood Test")
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.saveProfile(regimen, startDateMillis, isDarkTheme, useDynamic, useOnDeviceAi, apiKey = apiKey.takeIf { it.isNotBlank() })
                        onSaved()
                    },
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Save Settings")
                }
                
                Spacer(Modifier.height(32.dp))
                Text("Data Management", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    var showExport by remember { mutableStateOf(false) }
                    var showImport by remember { mutableStateOf(false) }
                    
                    Button(onClick = { showExport = true }) { Text("Export CSV") }
                    OutlinedButton(onClick = { showImport = true }) { Text("Import CSV") }
                    
                    if (showExport) {
                        val csv = viewModel.getExportCsv()
                        AlertDialog(
                            onDismissRequest = { showExport = false },
                            title = { Text("Exported Data") },
                            text = { 
                                OutlinedTextField(value = csv, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().height(200.dp))
                            },
                            confirmButton = { TextButton(onClick = { showExport = false }) { Text("Done") } }
                        )
                    }
                    if (showImport) {
                        var importStr by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showImport = false },
                            title = { Text("Import CSV Data") },
                            text = { 
                                OutlinedTextField(value = importStr, onValueChange = { importStr = it }, modifier = Modifier.fillMaxWidth().height(200.dp), label = { Text("Paste CSV here") })
                            },
                            confirmButton = { TextButton(onClick = { viewModel.parseImportCsv(importStr); showImport = false }) { Text("Import") } },
                            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
                        )
                    }
                }
                
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showBloodTestDialog) {
        var e2 by remember { mutableStateOf("") }
        var t by remember { mutableStateOf("") }
        var e2Unit by remember { mutableStateOf("pg/mL") }
        var tUnit by remember { mutableStateOf("ng/dL") }
        
        var showDate by remember { mutableStateOf(false) }
        var showTime by remember { mutableStateOf(false) }
        var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        var selectedHour by remember { mutableStateOf(12) }
        var selectedMinute by remember { mutableStateOf(0) }
        
        val e2Units = listOf("pg/mL", "pmol/L")
        val tUnits = listOf("ng/dL", "nmol/L")
        var e2Expanded by remember { mutableStateOf(false) }
        var tExpanded by remember { mutableStateOf(false) }

        val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())

        AlertDialog(
            onDismissRequest = { showBloodTestDialog = false },
            title = { Text("Log Blood Test") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDate = true }) {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis; set(java.util.Calendar.HOUR_OF_DAY, selectedHour); set(java.util.Calendar.MINUTE, selectedMinute) }
                        Text(format.format(cal.time))
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = e2, onValueChange = { e2 = it }, label = { Text("E2 Level") }, modifier = Modifier.weight(1f))
                        ExposedDropdownMenuBox(expanded = e2Expanded, onExpandedChange = { e2Expanded = !e2Expanded }, modifier = Modifier.weight(0.7f)) {
                            OutlinedTextField(value = e2Unit, onValueChange = {}, readOnly = true, modifier = Modifier.menuAnchor(), label = { Text("Unit") })
                            ExposedDropdownMenu(expanded = e2Expanded, onDismissRequest = { e2Expanded = false }) {
                                e2Units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { e2Unit = u; e2Expanded = false }) }
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("T Level") }, modifier = Modifier.weight(1f))
                        ExposedDropdownMenuBox(expanded = tExpanded, onExpandedChange = { tExpanded = !tExpanded }, modifier = Modifier.weight(0.7f)) {
                            OutlinedTextField(value = tUnit, onValueChange = {}, readOnly = true, modifier = Modifier.menuAnchor(), label = { Text("Unit") })
                            ExposedDropdownMenu(expanded = tExpanded, onDismissRequest = { tExpanded = false }) {
                                tUnits.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { tUnit = u; tExpanded = false }) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalE2 = e2.toFloatOrNull()?.let { if (e2Unit == "pmol/L") it / 3.671f else it }
                    val finalT = t.toFloatOrNull()?.let { if (tUnit == "nmol/L") it * 28.818f else it }
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis; set(java.util.Calendar.HOUR_OF_DAY, selectedHour); set(java.util.Calendar.MINUTE, selectedMinute) }
                    viewModel.addBloodTest(cal.timeInMillis, finalE2, finalT)
                    showBloodTestDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBloodTestDialog = false }) { Text("Cancel") }
            }
        )
        
        if (showDate) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = { TextButton(onClick = { selectedDateMillis = datePickerState.selectedDateMillis ?: selectedDateMillis; showDate = false; showTime = true }) { Text("Next") } }
            ) { DatePicker(state = datePickerState) }
        }
        
        if (showTime) {
            val timePickerState = rememberTimePickerState(initialHour = selectedHour, initialMinute = selectedMinute)
            AlertDialog(
                onDismissRequest = { showTime = false },
                confirmButton = { TextButton(onClick = { selectedHour = timePickerState.hour; selectedMinute = timePickerState.minute; showTime = false }) { Text("OK") } },
                text = { TimePicker(state = timePickerState) }
            )
        }
    }
}
