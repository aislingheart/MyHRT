package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editingMed = null; showDialog = true }, modifier = Modifier.testTag("add_med_button")) {
                Icon(Icons.Filled.Add, contentDescription = "Add Medication")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "My Medications",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            if (medications.isEmpty()) {
                Text(
                    "No medications added yet.",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(medications) { med ->
                        val isTakenToday = (System.currentTimeMillis() - med.lastTakenDateMillis) < (24L * 60 * 60 * 1000)

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                editingMed = med
                                showDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(med.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("${med.method} - ${med.dose}")
                                    Text("Frequency: ${med.frequency}")
                                    if (med.isReminderEnabled) {
                                        Text(
                                            "Reminder: ${String.format("%02d:%02d", med.reminderHour, med.reminderMinute)}",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    IconButton(onClick = { viewModel.removeMedication(med.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                    if (!isTakenToday) {
                                        Button(onClick = { viewModel.markMedicationTaken(med) }) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Take")
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.undoMedicationTaken(med) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
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
                    val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("MED_ID", name.hashCode()) // Weak ID for simplicity
                        putExtra("MED_NAME", name)
                    }
                    val pi = android.app.PendingIntent.getBroadcast(
                        context, name.hashCode(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                    
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, hr)
                        set(java.util.Calendar.MINUTE, min)
                        set(java.util.Calendar.SECOND, 0)
                        if (before(java.util.Calendar.getInstance())) {
                            add(java.util.Calendar.DATE, 1)
                        }
                    }
                    
                    try {
                        alarmManager.setRepeating(
                            android.app.AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            android.app.AlarmManager.INTERVAL_DAY,
                            pi
                        )
                    } catch (e: SecurityException) {
                        // In Android 14+ need exactly exact alarms permission
                    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (medToEdit != null) "Edit Medication" else "Add Medication") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
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
                        modifier = Modifier.weight(0.5f)
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
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

                Button(onClick = { showTimePicker = true }) {
                    Text("Routine Dosage Time: ${String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)}")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                    Text("Enable Notification")
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
            title = { Text("Select Reminder Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("OK") } }
        )
    }
}
