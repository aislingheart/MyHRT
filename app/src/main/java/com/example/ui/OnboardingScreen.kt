package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import com.example.BuildConfig
import com.example.api.GeminiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: HRTViewModel, onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val maxPages = 5
    
    // Page 1 Data
    var startDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var regimen by remember { mutableStateOf("Feminizing") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Page 2 Data
    var showMedDialog by remember { mutableStateOf(false) }
    var showBloodTestDialog by remember { mutableStateOf(false) }
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    
    // Page 3 Data
    var wantsAi by remember { mutableStateOf<Boolean?>(null) }
    
    // Page 4 Data
    var aiType by remember { mutableStateOf("Cloud") }
    var aiStatusText by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var showApiKeyPrompt by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if(currentPage < 4) "Setup Profile (${currentPage+1}/4)" else "Welcome") }) },
        bottomBar = {
            BottomAppBar {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (currentPage > 0 && currentPage < 4) {
                        TextButton(onClick = { 
                            if (currentPage == 4 && wantsAi == false) currentPage = 2
                            else currentPage-- 
                        }) { Text("Back") }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    if (currentPage < 4) {
                        Button(onClick = { 
                            if (currentPage == 2 && wantsAi == false) currentPage = 4
                            else currentPage++ 
                        }) { Text("Next") }
                    } else {
                        Button(onClick = {
                            viewModel.saveProfile(regimen, startDateMillis, useOnDeviceAi = (aiType == "Local" && wantsAi == true), apiKey = apiKey.takeIf { it.isNotBlank() })
                            onComplete()
                        }) { Text("Finish Setup") }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(targetState = currentPage, label = "onboarding") { page ->
                when (page) {
                    0 -> PageOne(startDateMillis, { startDateMillis = it }, regimen, { regimen = it })
                    1 -> PageTwo(viewModel, medications)
                    2 -> PageThree(wantsAi) { wantsAi = it }
                    3 -> PageFour(aiType, { aiType = it }, aiStatusText, apiKey, { apiKey = it }, {
                        scope.launch {
                            aiStatusText = "Testing..."
                            try {
                                aiStatusText = GeminiClient.generateInsight("Reply exactly with 'AI Working!'", "You are a tester.", useNano = (aiType == "Local"), providedApiKey = apiKey)
                            } catch (e: Throwable) {
                                aiStatusText = "Failed: ${e.message}"
                            }
                        }
                    })
                    4 -> PageFive()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOne(startDateMillis: Long, onDateSelected: (Long) -> Unit, regimen: String, onRegimenSelected: (String) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Regimen", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row {
            listOf("Feminizing", "Masculinizing").forEach { opt ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = regimen == opt, onClick = { onRegimenSelected(opt) })
                    Text(opt)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("When did you start HRT?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onDateSelected(System.currentTimeMillis()) }) { Text("Right Now") }
            OutlinedButton(onClick = { showDate = true }) { Text("A While Back") }
        }
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
        val fmt = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        Text("Selected Date: ${fmt.format(cal.time)}", modifier = Modifier.padding(top=16.dp), style = MaterialTheme.typography.labelLarge)
    }
    
    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { onDateSelected(dateState.selectedDateMillis ?: startDateMillis); showDate = false }) { Text("OK") } }
        ) { DatePicker(state = dateState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTwo(viewModel: HRTViewModel, medications: List<com.example.data.Medication>) {
    var showMedDialog by remember { mutableStateOf(false) }
    var showBloodDialog by remember { mutableStateOf(false) }
    var medToEdit by remember { mutableStateOf<com.example.data.Medication?>(null) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("What meds are you taking?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        medications.forEach { med ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                onClick = {
                    medToEdit = med
                    showMedDialog = true
                }
            ) {
                Text("${med.name} - ${med.dose}", modifier = Modifier.padding(16.dp))
            }
        }
        Button(onClick = { 
            medToEdit = null
            showMedDialog = true 
        }) { Text("+ Add Medication") }
        
        Spacer(Modifier.height(32.dp))
        Text("Do you have blood results?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { showBloodDialog = true }) { Text("+ Log Lab Results") }
    }
    
    if (showMedDialog) {
        AddMedicationDialog(
            medToEdit = medToEdit, 
            onDismiss = { showMedDialog = false }, 
            onAdd = { n, m, d, f, r, h, mn ->
                if (medToEdit != null) {
                    viewModel.updateMedication(medToEdit!!.copy(name = n, method = m, dose = d, frequency = f, isReminderEnabled = r, reminderHour = h, reminderMinute = mn))
                } else {
                    viewModel.addMedication(n, m, d, f, r, h, mn)
                }
                showMedDialog = false
            }
        )
    }
    // Simplistic blood dialog for onboarding
    if (showBloodDialog) {
        var e2 by remember { mutableStateOf("") }
        var t by remember { mutableStateOf("") }
        var e2Unit by remember { mutableStateOf("pg/mL") }
        var tUnit by remember { mutableStateOf("ng/dL") }
        var e2DropdownExpanded by remember { mutableStateOf(false) }
        var tDropdownExpanded by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showBloodDialog = false },
            title = { Text("Log Blood Test") },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = e2, onValueChange = { e2 = it }, label = { Text("E2 Level") }, modifier = Modifier.weight(1f))
                        ExposedDropdownMenuBox(expanded = e2DropdownExpanded, onExpandedChange = { e2DropdownExpanded = !e2DropdownExpanded }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(value = e2Unit, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = e2DropdownExpanded) }, modifier = Modifier.menuAnchor())
                            ExposedDropdownMenu(expanded = e2DropdownExpanded, onDismissRequest = { e2DropdownExpanded = false }) {
                                DropdownMenuItem(text = { Text("pg/mL") }, onClick = { e2Unit = "pg/mL"; e2DropdownExpanded = false })
                                DropdownMenuItem(text = { Text("pmol/L") }, onClick = { e2Unit = "pmol/L"; e2DropdownExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("T Level") }, modifier = Modifier.weight(1f))
                        ExposedDropdownMenuBox(expanded = tDropdownExpanded, onExpandedChange = { tDropdownExpanded = !tDropdownExpanded }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(value = tUnit, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tDropdownExpanded) }, modifier = Modifier.menuAnchor())
                            ExposedDropdownMenu(expanded = tDropdownExpanded, onDismissRequest = { tDropdownExpanded = false }) {
                                DropdownMenuItem(text = { Text("ng/dL") }, onClick = { tUnit = "ng/dL"; tDropdownExpanded = false })
                                DropdownMenuItem(text = { Text("nmol/L") }, onClick = { tUnit = "nmol/L"; tDropdownExpanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val e2Val = e2.toFloatOrNull()?.let { if (e2Unit == "pmol/L") (it / 3.67).toFloat() else it }
                    val tVal = t.toFloatOrNull()?.let { if (tUnit == "nmol/L") (it * 28.8).toFloat() else it }
                    viewModel.addBloodTest(System.currentTimeMillis(), e2Val, tVal)
                    showBloodDialog = false
                }) { Text("Save") }
            }
        )
    }
}

@Composable
fun PageThree(wantsAi: Boolean?, onSelect: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AI Assistant", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Do you want AI to help you on this journey?", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onSelect(true) }, colors = ButtonDefaults.buttonColors(containerColor = if (wantsAi == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)) { Text("Yes") }
            Button(onClick = { onSelect(false) }, colors = ButtonDefaults.buttonColors(containerColor = if (wantsAi == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)) { Text("No") }
        }
    }
}

@Composable
fun PageFour(aiType: String, onAiTypeChange: (String) -> Unit, statusText: String, apiKey: String, onApiKeyChange: (String) -> Unit, onTest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Local or Cloud AI?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(selected = aiType == "Local", onClick = { onAiTypeChange("Local") }, label = { Text("Local (Gemini Nano)") })
            FilterChip(selected = aiType == "Cloud", onClick = { onAiTypeChange("Cloud") }, label = { Text("Cloud (Gemini API)") })
        }
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(value = apiKey, onValueChange = onApiKeyChange, label = { Text("Gemini API Key (Optional for Local)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        
        if (aiType == "Cloud") {
            Text("To use Cloud AI, you must enter a Gemini API Key above or in the AI Studio Secrets panel named 'GEMINI_API_KEY'.", color = MaterialTheme.colorScheme.primary)
        } else {
            Text("Local AI requires Google AI Core to be installed and supported on your device (Pixel 8+ / S24+).", color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(Modifier.height(16.dp))
        Button(onClick = onTest) { Text("Test Configuration") }
        if (statusText.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(statusText, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PageFive() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌸", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Bloom", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Your journey begins here.", style = MaterialTheme.typography.bodyMedium)
    }
}

