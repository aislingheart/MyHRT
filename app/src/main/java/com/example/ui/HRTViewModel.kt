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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    private val _aiErrorState = MutableStateFlow<String?>(null)
    val aiErrorState: StateFlow<String?> = _aiErrorState

    val recentSessions = repository.allChatSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentChatMessages: StateFlow<List<com.example.data.ChatMessage>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getMessagesForSession(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createNewChatSession() {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val session = com.example.data.ChatSession(id, "Chat " + java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
            repository.addChatSession(session)
            _currentSessionId.value = id
        }
    }

    fun loadChatSession(sessionId: String?) {
        _currentSessionId.value = sessionId
    }

    fun addChatMessage(text: String, isUser: Boolean, sourcesQuery: String? = null) {
        viewModelScope.launch {
            var sid = _currentSessionId.value
            if (sid == null) {
                sid = java.util.UUID.randomUUID().toString()
                repository.addChatSession(com.example.data.ChatSession(sid, "Chat " + java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())))
                _currentSessionId.value = sid
            }
            repository.addChatMessage(com.example.data.ChatMessage(sessionId = sid, text = text, isUser = isUser, sourcesQuery = sourcesQuery))
        }
    }

    fun getSimulatedLevels(): String {
        val meds = medications.value
        val bloods = bloodTests.value
        val profile = userProfile.value ?: return "No profile data"
        val isFem = profile.regimenType == "Feminizing"
        val e2 = com.example.data.Pharmacokinetics.calculateEstradiol(meds, 0.0, bloods, profile.startDateMillis, isFem)
        val t = com.example.data.Pharmacokinetics.calculateTestosterone(meds, 0.0, bloods, profile.startDateMillis, isFem, e2)
        return "Estimated current levels -> Estradiol (E2): ${String.format(java.util.Locale.US, "%.1f", e2)} pg/mL, Testosterone (T): ${String.format(java.util.Locale.US, "%.1f", t)} ng/dL"
    }

    fun getMilestonesStatus(): String {
        val checks = milestoneChecks.value
        return if (checks.isEmpty()) "No milestones achieved yet." else checks.joinToString("\n") { "- ${it.milestoneId} (Achieved on ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.achievedDateMillis))})" }
    }

    fun getBloodTestsHistory(): String {
        val tests = bloodTests.value
        if (tests.isEmpty()) return "No blood tests logged."
        return tests.joinToString("\n") { "Date: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.dateMillis))} | E2: ${it.e2Level ?: "N/A"} pg/mL | T: ${it.tLevel ?: "N/A"} ng/dL" }
    }

    fun saveProfile(regimenType: String, startDateMillis: Long, isDarkTheme: Boolean? = null, useDynamic: Boolean = true, useOnDeviceAi: Boolean = false, apiKey: String? = null, aiModel: String? = null, userName: String? = null, isAiEnabled: Boolean? = null) {
        viewModelScope.launch {
            val currentApiKey = apiKey ?: userProfile.value?.apiKey
            val currentAiModel = aiModel ?: userProfile.value?.selectedAiModel ?: "gemini-3.5-flash"
            val currentName = userName ?: userProfile.value?.userName ?: "Friend"
            val currentAiEnabled = isAiEnabled ?: userProfile.value?.isAiEnabled ?: true
            repository.saveUserProfile(
                UserProfile(
                    regimenType = regimenType,
                    startDateMillis = startDateMillis,
                    isDarkTheme = isDarkTheme,
                    useDynamicColor = useDynamic,
                    useOnDeviceAi = useOnDeviceAi,
                    apiKey = currentApiKey,
                    selectedAiModel = currentAiModel,
                    userName = currentName,
                    isAiEnabled = currentAiEnabled
                )
            )
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

    fun getGenericOfflineInsight(regimenType: String, daysOnHRT: Long, userName: String): String {
        val isFem = regimenType == "Feminizing"
        val greeting = if (userName.isNotEmpty() && userName != "Friend") "Hello $userName, here is your progress expectation based on day $daysOnHRT:\n\n" else "Progress expectation based on day $daysOnHRT:\n\n"
        
        return if (isFem) {
            greeting + when {
                daysOnHRT < 7 -> "Week 1 (Day $daysOnHRT): Early physiological adaptation. Minor psychological relief, subtle skin softening, and early fluctuations in libido may begin within the first few days."
                daysOnHRT < 30 -> "Weeks 2-4 (Day $daysOnHRT): Onset of nipple sensitivity/tenderness behind the areola (initiation of breast buds/thelarche), minor muscle strength decrease, and minor facial oil production drop."
                daysOnHRT < 90 -> "Months 2-3 (Day $daysOnHRT): Early breast development. Body hair growth rate slowly starts decreasing, skin becomes softer and less oily, and emotional sensitivity/changes become more noticeable."
                daysOnHRT < 180 -> "Months 3-6 (Day $daysOnHRT): Breast development continues (Tanner Stage 2-3). fat redistribution to hips, thighs, and face is underway. Decreased muscle mass and strength."
                else -> "Months 6+ (Day $daysOnHRT): Pronounced feminization. Breast expansion continues up to 2-3 years. Skin softening and fat redistribution maximize over 2-5 years as sperm production and testicular volume diminish."
            }
        } else {
            greeting + when {
                daysOnHRT < 7 -> "Week 1 (Day $daysOnHRT): Early psychological relief. Energy or libido increase may begin within the first few days. Skin becomes slightly oilier, and initial voice crackling or throat tickle is possible."
                daysOnHRT < 30 -> "Weeks 2-4 (Day $daysOnHRT): Increased muscle mass index, minor body odor transition, oilier skin/acne development, and initial clitoral enlargement (often the first physical change)."
                daysOnHRT < 90 -> "Months 2-3 (Day $daysOnHRT): Facial and body hair development begins. Cessation of menses (amenorrhea) typically starts. Voice begins to deepen permanently."
                daysOnHRT < 180 -> "Months 3-6 (Day $daysOnHRT): Noticeable voice deepening. Facial and body hair density increases. Significant fat redistribution away from hips and thighs."
                else -> "Months 6+ (Day $daysOnHRT): Maximum masculinization. Voice pitch stabilizes. Substantial beard development, clitoral hypertrophy, and muscle hypertrophy continue to mature over several years."
            }
        }
    }

    fun fetchInsight(profile: UserProfile?) {
        if (profile == null) return
        val daysOnHRT = ((System.currentTimeMillis() - profile.startDateMillis) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        
        if (profile.isAiEnabled == false) {
            _aiErrorState.value = null
            _insightState.value = getGenericOfflineInsight(profile.regimenType, daysOnHRT, profile.userName)
            return
        }

        // Pass current meds and tests into scope so the model really understands
        val curMeds = medications.value.joinToString { "${it.name} ${it.dose} ${it.frequency}" }
        val curTests = bloodTests.value.joinToString { "E2: ${it.e2Level ?: "N/A"}, T: ${it.tLevel ?: "N/A"}" }
        
        viewModelScope.launch {
            _insightState.value = "Fetching insights..."
            _aiErrorState.value = null
            try {
                val system = "You are an expert endocrinology AI assistant specializing in transgender Hormone Replacement Therapy (HRT). Understand and digest clinical variables carefully. Be solely professional in advice and suggestions, grounding responses in true academic research (like WPATH 8 or Endocrine Society). Never hallucinate medical truths. Emphasize actual day count constraints."
                val prompt = "The user is on a ${profile.regimenType} HRT regimen. They have literally been on it for $daysOnHRT days (so if this is > 5, DO NOT give 'Day 0' or 'Starting out' generic advice, speak to their actual day count). Current Meds: [$curMeds]. Recent Tests: [$curTests]. What symptom changes or side effects should they expect right around day $daysOnHRT? Give professional, clinical advice grounded in literature."
                val response = com.example.api.GeminiClient.generateInsight(prompt, system, profile.useOnDeviceAi == true, profile.apiKey, profile.selectedAiModel).first
                
                if (response.startsWith("Unable to generate") || response.contains("Error: ") || response.contains("Please configure your Gemini API Key")) {
                    // It failed or is unconfigured
                    val errCode = if (response.contains("Error:")) {
                        val parsed = response.substringAfter("Error:").take(25).trim()
                        if (parsed.contains(" ")) parsed.substringBefore(" ") else parsed
                    } else "API_KEY_MISSING"
                    _aiErrorState.value = errCode
                    _insightState.value = getGenericOfflineInsight(profile.regimenType, daysOnHRT, profile.userName)
                } else {
                    _insightState.value = response
                    _aiErrorState.value = null
                }
            } catch (e: Throwable) {
                _aiErrorState.value = e.message?.take(20)?.trim() ?: "API_ERROR"
                _insightState.value = getGenericOfflineInsight(profile.regimenType, daysOnHRT, profile.userName)
            }
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
