package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(viewModel: HRTViewModel) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    var editingMed by remember { mutableStateOf<com.example.data.Medication?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingMed = null; showDialog = true },
                modifier = Modifier.testTag("add_med_button"),
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Medication") },
                text = { Text("Add Med") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "My Medications",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Manage your HRT regimen and set reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (medications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No medications added yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(medications) { med ->
                        val isTakenToday = (System.currentTimeMillis() - med.lastTakenDateMillis) < (24L * 60 * 60 * 1000)

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().clickable {
                                editingMed = med
                                showDialog = true
                            },
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(med.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${med.method} • ${med.dose}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Frequency: ${med.frequency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    if (med.isReminderEnabled) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "🔔 Reminder: ${String.format("%02d:%02d", med.reminderHour, med.reminderMinute)}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    IconButton(onClick = { viewModel.removeMedication(med.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                    if (!isTakenToday) {
                                        Button(onClick = { viewModel.markMedicationTaken(med) }, shape = MaterialTheme.shapes.medium) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Take")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.undoMedicationTaken(med) },
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Undo", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AddMedicationDialog(
            medToEdit = editingMed,
            onDismiss = { showDialog = false; editingMed = null },
            onAdd = { name, method, dose, freq, reminder, hr, min ->
                if (editingMed != null) {
                    viewModel.updateMedication(
                        editingMed!!.copy(
                            name = name,
                            method = method,
                            dose = dose,
                            frequency = freq,
                            isReminderEnabled = reminder,
                            reminderHour = hr,
                            reminderMinute = min
                        )
                    )
                } else {
                    viewModel.addMedication(name, method, dose, freq, reminder, hr, min)
                }
                showDialog = false
                editingMed = null
                
                if (reminder) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                    val fakeMed = com.example.data.Medication(
                        id = 0, name = name, method = method, dose = dose, frequency = freq,
                        isReminderEnabled = true, reminderHour = hr, reminderMinute = min,
                        startDateMillis = System.currentTimeMillis()
                    )
                    ReminderUtil.scheduleAlarms(context, alarmManager, fakeMed)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationDialog(medToEdit: com.example.data.Medication?, onDismiss: () -> Unit, onAdd: (String, String, String, String, Boolean, Int, Int) -> Unit) {
    var name by remember { mutableStateOf(medToEdit?.name ?: "Estradiol Valerate") }
    var nameExpanded by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(medToEdit?.method ?: "Oral") }
    var doseValue by remember { mutableStateOf(medToEdit?.dose?.replace(Regex("[^0-9.]"), "") ?: "") }
    var doseUnit by remember { mutableStateOf(medToEdit?.dose?.replace(Regex("[0-9. ]"), "")?.takeIf { it.isNotBlank() } ?: "mg") }
    var frequency by remember { mutableStateOf(medToEdit?.frequency ?: "Daily") }
    var freqExpanded by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(medToEdit?.isReminderEnabled ?: false) }
    var methodExpanded by remember { mutableStateOf(false) }

    val initialHr = medToEdit?.reminderHour ?: 9
    val initialMin = medToEdit?.reminderMinute ?: 0
    val timePickerState = rememberTimePickerState(initialHour = initialHr, initialMinute = initialMin)
    var showTimePicker by remember { mutableStateOf(false) }

    val names = listOf("Estradiol Valerate", "Estradiol Cypionate", "Estradiol Enanthate", "Estradiol Benzoate", "Estradiol Hemihydrate", "Estradiol", "Estradiol (Other)", "Testosterone Cypionate", "Testosterone Enanthate", "Testosterone (Other)", "Spironolactone", "Cyproterone Acetate", "Bicalutamide", "Progesterone")
    val methods = listOf("Oral", "Sublingual", "SubQ Injection", "IM Injection", "Gel", "Patch")
    val frequencies = listOf("Daily", "Twice Daily", "Every 3 days", "Every 5 days", "Weekly", "Bi-weekly", "Monthly", "Every 3 months")

    val recommendation = when {
        frequency.contains("Twice Daily") -> "💡 Recommended: Take doses 12 hours apart (e.g., 9:00 AM & 9:00 PM) for stable levels."
        method == "Sublingual" -> "💡 Recommended: Avoid eating/drinking for 15-30 mins after dissolving."
        method == "Oral" -> "💡 Recommended: Take with a small amount of food to improve absorption."
        method.contains("Gel") -> "💡 Recommended: Apply to clean, dry skin after a shower."
        method.contains("Injection") -> "💡 Recommended: Rotate injection sites to prevent tissue scarring."
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (medToEdit != null) "Edit Medication" else "Add Medication", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = nameExpanded,
                    onExpandedChange = { nameExpanded = !nameExpanded }
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Name") },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = nameExpanded,
                        onDismissRequest = { nameExpanded = false }
                    ) {
                        names.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    name = selectionOption
                                    nameExpanded = false
                                }
                            )
                        }
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = !methodExpanded }
                ) {
                    OutlinedTextField(
                        value = method,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Method") },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = methodExpanded,
                        onDismissRequest = { methodExpanded = false }
                    ) {
                        methods.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    method = selectionOption
                                    methodExpanded = false
                                }
                            )
                        }
                    }
                }

                var doseUnitExpanded by remember { mutableStateOf(false) }
                val doseUnits = listOf("mg", "mL", "mcg", "patches", "pumps")
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = doseValue, 
                        onValueChange = { doseValue = it }, 
                        label = { Text("Dose") }, 
                        modifier = Modifier.weight(1f)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = doseUnitExpanded,
                        onExpandedChange = { doseUnitExpanded = !doseUnitExpanded },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        OutlinedTextField(
                            value = doseUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = doseUnitExpanded,
                            onDismissRequest = { doseUnitExpanded = false }
                        ) {
                            doseUnits.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        doseUnit = u
                                        doseUnitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = freqExpanded,
                    onExpandedChange = { freqExpanded = !freqExpanded }
                ) {
                    OutlinedTextField(
                        value = frequency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = freqExpanded,
                        onDismissRequest = { freqExpanded = false }
                    ) {
                        frequencies.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    frequency = selectionOption
                                    freqExpanded = false
                                }
                            )
                        }
                    }
                }

                if (recommendation.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = recommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Base Routine Time: ${String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)}")
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                    Text("Enable Notifications", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onAdd(name, method, "$doseValue $doseUnit", frequency, reminder, timePickerState.hour, timePickerState.minute) 
            }, modifier = Modifier.testTag("save_med_button")) {
                Text(if (medToEdit != null) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("OK") } }
        )
    }
}

