package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.DailyLog
import com.example.data.HRTRepository
import com.example.data.Medication
import com.example.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class HRTViewModel(private val repository: HRTRepository) : ViewModel() {

    val userProfile = repository.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val medications = repository.allMedications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val logs = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val bloodTests = repository.allBloodTests.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val milestoneChecks = repository.allMilestoneChecks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _insightState = MutableStateFlow<String?>(null)
    val insightState: StateFlow<String?> = _insightState

    fun saveProfile(regimenType: String, startDateMillis: Long, isDarkTheme: Boolean? = null, useDynamic: Boolean = true, useOnDeviceAi: Boolean = false, apiKey: String? = null) {
        viewModelScope.launch {
            val currentApiKey = apiKey ?: userProfile.value?.apiKey
            repository.saveUserProfile(UserProfile(regimenType = regimenType, startDateMillis = startDateMillis, isDarkTheme = isDarkTheme, useDynamicColor = useDynamic, useOnDeviceAi = useOnDeviceAi, apiKey = currentApiKey))
        }
    }

    fun setTheme(isDark: Boolean, useDynamic: Boolean = true) {
        viewModelScope.launch {
            val curr = userProfile.value
            if (curr != null) {
                repository.saveUserProfile(curr.copy(isDarkTheme = isDark, useDynamicColor = useDynamic))
            } else {
                repository.saveUserProfile(UserProfile(regimenType = "Feminizing", startDateMillis = System.currentTimeMillis(), isDarkTheme = isDark, useDynamicColor = useDynamic))
            }
        }
    }

    fun addMedication(name: String, method: String, dose: String, frequency: String, isReminder: Boolean, hour: Int = 9, minute: Int = 0) {
        viewModelScope.launch {
            val sDate = userProfile.value?.startDateMillis ?: System.currentTimeMillis()
            repository.addMedication(
                Medication(
                    name = name,
                    method = method,
                    dose = dose,
                    frequency = frequency,
                    nextRenewalDateMillis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
                    isReminderEnabled = isReminder,
                    reminderHour = hour,
                    reminderMinute = minute,
                    startDateMillis = sDate
                )
            )
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            repository.addMedication(medication)
        }
    }

    fun markMedicationTaken(medication: Medication) {
        viewModelScope.launch {
            repository.addMedication(medication.copy(lastTakenDateMillis = System.currentTimeMillis()))
        }
    }

    fun undoMedicationTaken(medication: Medication) {
        viewModelScope.launch {
            repository.addMedication(medication.copy(lastTakenDateMillis = 0L))
        }
    }

    fun removeMedication(id: Int) {
        viewModelScope.launch {
            repository.removeMedication(id)
        }
    }

    fun logToday(notes: String, mood: String, symptomFlags: String) {
        viewModelScope.launch {
            repository.addLog(
                DailyLog(
                    dateMillis = System.currentTimeMillis(),
                    notes = notes,
                    mood = mood,
                    symptomFlags = symptomFlags
                )
            )
        }
    }

    fun addBloodTest(dateMillis: Long, e2: Float?, t: Float?) {
        viewModelScope.launch {
            repository.addBloodTest(
                com.example.data.BloodTestResult(
                    dateMillis = dateMillis,
                    e2Level = e2,
                    tLevel = t
                )
            )
        }
    }

    fun removeBloodTest(id: Int) {
        viewModelScope.launch { repository.removeBloodTest(id) }
    }

    fun toggleMilestone(id: String, isChecked: Boolean, dateMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            if (isChecked) {
                repository.checkMilestone(com.example.data.MilestoneCheck(id, dateMillis))
            } else {
                repository.uncheckMilestone(id)
            }
        }
    }

    fun fetchInsight(profile: UserProfile?) {
        if (profile == null) return
        val daysOnHRT = ((System.currentTimeMillis() - profile.startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        
        // Pass current meds and tests into scope so the model really understands
        val curMeds = medications.value.joinToString { "${it.name} ${it.dose} ${it.frequency}" }
        val curTests = bloodTests.value.joinToString { "E2: ${it.e2Level ?: "N/A"}, T: ${it.tLevel ?: "N/A"}" }
        
        viewModelScope.launch {
            _insightState.value = "Fetching insights..."
            val system = "You are an expert endocrinology AI assistant specializing in transgender Hormone Replacement Therapy (HRT). Understand and digest clinical variables carefully. Be solely professional in advice and suggestions, grounding responses in true academic research (like WPATH 8 or Endocrine Society). Never hallucinate medical truths. Emphasize actual day count constraints."
            val prompt = "The user is on a ${profile.regimenType} HRT regimen. They have literally been on it for $daysOnHRT days (so if this is > 5, DO NOT give 'Day 0' or 'Starting out' generic advice, speak to their actual day count). Current Meds: [$curMeds]. Recent Tests: [$curTests]. What symptom changes or side effects should they expect right around day $daysOnHRT? Give professional, clinical advice grounded in literature."
            val response = GeminiClient.generateInsight(prompt, system, profile.useOnDeviceAi == true, profile.apiKey)
            _insightState.value = response
        }
    }

    fun getExportCsv(): String {
        val b = StringBuilder()
        b.append("Type,DateMillis,Value1,Value2,Value3,Value4\n")
        userProfile.value?.let { p ->
            b.append("Profile,${p.startDateMillis},${p.regimenType},${p.isDarkTheme},${p.useDynamicColor},${p.useOnDeviceAi}\n")
        }
        medications.value.forEach { m ->
            b.append("Medication,${m.startDateMillis},${m.name},${m.dose},${m.frequency},${m.method}\n")
        }
        bloodTests.value.forEach { t ->
            b.append("BloodTest,${t.dateMillis},${t.e2Level},${t.tLevel},,\n")
        }
        logs.value.forEach { l ->
            b.append("Log,${l.dateMillis},\"${l.mood}\",\"${l.symptomFlags}\",\"${l.notes.replace("\n", " ")}\",\n")
        }
        return b.toString()
    }

    fun parseImportCsv(csv: String) {
        viewModelScope.launch {
            csv.lines().forEach { line ->
                val parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim('"') }
                if (parts.size >= 6) {
                    val type = parts[0]
                    val time = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                    when(type) {
                        "Medication" -> addMedication(parts[2], parts[5], parts[3], parts[4], false)
                        "BloodTest" -> addBloodTest(time, parts[2].replace("null", "").toFloatOrNull(), parts[3].replace("null", "").toFloatOrNull())
                        "Log" -> logToday(parts[4], parts[2], parts[3])
                    }
                }
            }
        }
    }
}
