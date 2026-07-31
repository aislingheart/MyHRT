package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalUriHandler
import com.example.api.GeminiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: HRTViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    
    val messages by viewModel.currentChatMessages.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    var showHistoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("LilyAI \uD83C\uDF38") },
                actions = {
                    IconButton(onClick = { viewModel.createNewChatSession() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Chat")
                    }
                    Box {
                        IconButton(onClick = { showHistoryMenu = true }) {
                            Icon(Icons.Filled.History, contentDescription = "Recent Chats")
                        }
                        DropdownMenu(
                            expanded = showHistoryMenu,
                            onDismissRequest = { showHistoryMenu = false }
                        ) {
                            if (recentSessions.isEmpty()) {
                                DropdownMenuItem(text = { Text("No recent chats") }, onClick = { showHistoryMenu = false })
                            } else {
                                recentSessions.forEach { session ->
                                    DropdownMenuItem(
                                        text = { Text(session.title) },
                                        onClick = {
                                            viewModel.loadChatSession(session.id)
                                            showHistoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            ) 
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            if (userProfile == null) {
                Text("Please configure your profile first.")
                return@Column
            }
            
            if (messages.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Hi, I'm Lily! \uD83C\uDF38 Ask me about your HRT journey, medications, or timeline expectations. I have access to your profile context.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                    items(messages.reversed()) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(msg.text)
                                    if (msg.sourcesQuery != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val uriHandler = LocalUriHandler.current
                                        OutlinedButton(onClick = { 
                                            uriHandler.openUri("https://www.google.com/search?q=${android.net.Uri.encode(msg.sourcesQuery)}")
                                        }) {
                                            Text("🔍 Sources")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isLoading) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Lily something...") },
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = input.isNotBlank() && !isLoading,
                    onClick = {
                        val userMsg = input
                        input = ""
                        viewModel.addChatMessage(userMsg, isUser = true)
                        isLoading = true
                        
                        val curMeds = medications.joinToString { "${it.name} ${it.dose}" }
                        val curLog = logs.firstOrNull()?.notes ?: "None"
                        val system = """
You are Lily 🌸, a warmly professional, deeply empathetic, and highly knowledgeable medical assistant specializing in transgender and gender-diverse (TGD) Hormone Replacement Therapy (HRT).

=== USER CONTEXT ===
Regimen Type: ${userProfile!!.regimenType} HRT
HRT Start Date: ${java.util.Date(userProfile!!.startDateMillis)}
Current Medications: $curMeds
Recent Journal Note: $curLog

=== YOUR CLINICAL KNOWLEDGE BASE ===
You are grounded in the following peer-reviewed research and clinical guidelines. Apply this knowledge to all answers:

--- FEMINIZING HRT TIMELINES (Endocrine Society / WPATH SOC8) ---
• Decreased libido & erections: onset 1-3 months, maximum 3-6 months. Reversible.
• Cessation of male pattern balding: onset 1-3 months, maximum 1-2 years. Reversible (halting). Finasteride/dutasteride are complementary adjuncts.
• Skin softening & reduced oiliness: onset 3-6 months, ongoing. Reversible.
• Breast development (thelarche): onset 3-6 months, maximum 2-3 years. IRREVERSIBLE. Begins as tender buds beneath areola. 2024 Amsterdam UMC RCT (n=90): adding micronized progesterone increases breast volume up to 30% (~1 cup size). Wait 1-2 years before augmentation surgery.
• Body fat redistribution: onset 3-6 months, maximum 2-5 years. Partial. Fat migrates to hips/thighs/glutes/face.
• Decreased muscle mass & strength: onset 3-6 months, maximum 1-2 years. Reversible.
• Decreased testicular volume: onset 3-6 months, maximum 2-3 years. Likely PERMANENT. Sperm banking strongly advised pre-HRT.
• Body & facial hair slowdown: onset 6-12 months, maximum 3-5 years. Facial hair rarely disappears without laser/electrolysis.
• Emotional lability & increased empathy: onset 1-3 months. Neurochemical — estrogen lowers crying threshold, increases empathy. Stanford 2022: early GAHT = significantly better mental health long-term.
• PMS-like cyclical symptoms: onset 2-6 months, ongoing. Linked to estradiol troughs — especially from injection cycles. Stabilise dosing to reduce severity. IAPD-validated experience.
• Sperm production decrease: variable onset. Possibly permanent after prolonged therapy.

--- MASCULINIZING HRT TIMELINES (Endocrine Society / WPATH SOC8) ---
• Skin oiliness & acne: onset 1-6 months, maximum 1-2 years. Reversible. Often cystic — may need dermatology.
• Cessation of menses (amenorrhea): onset 1-6 months, maximum 1-2 years. Reversible. Full amenorrhea by 6 months confirms adequate androgen exposure. IMPORTANT: testosterone is NOT a reliable contraceptive.
• Clitoral enlargement (bottom growth): onset 1-6 months (initial palpable within 1-3 months), maximum 1-2 years. IRREVERSIBLE. Growth 1-5 cm. Temporary tactile hypersensitivity is normal.
• Body fat redistribution: onset 1-6 months, maximum 2-5 years. Partial. Fat moves to abdomen/visceral compartment. Karolinska MRI study: 70% increase in visceral fat, elevated LDL, reduced HDL — metabolic monitoring important.
• Facial & body hair growth: onset 3-6 months, maximum 4-5 years. IRREVERSIBLE. Genetically determined density.
• Voice deepening: onset 6-12 months, maximum 1-2 years. IRREVERSIBLE. PMC acoustic study: 183 Hz → 134 Hz within 37 weeks. 'Cracking' phase is normal. Voice is not destroyed — speech-language pathology assists.
• Increased muscle mass & strength: onset 6-12 months, maximum 2-5 years. Reversible. Karolinska: 21% lean volume increase average.
• Scalp hair loss: onset > 12 months, genetically variable. IRREVERSIBLE. 5-ARIs (finasteride/dutasteride) can halt without counteracting virilization.
• Vulvovaginal atrophy: onset 1-6 months. Reversible with treatment. Topical vaginal estrogen (Estrace, Imvexxy) is gold standard — systemic absorption negligible, will NOT re-feminize or re-induce menstruation.
• Emotional changes / T-cold: onset 1-6 months. Physiological crying inhibition due to elevated T + suppressed E2. Not indicative of reduced empathy. Mood lability improved by weekly rather than biweekly injections.
• Erythrocytosis risk: ~11% of trans men develop hematocrit >50% over treatment. Hematocrit >54% warrants dose reduction or phlebotomy. Risk factors: high BMI, older age, tobacco use. Monitor regularly.

--- ANTI-ANDROGENS ---
• Spironolactone (US first-line): competitive aldosterone antagonist + androgen receptor blocker. Hyperkalemia risk is LOW (2.5% overall, 1.5% under 45). Community reports of 'brain fog', lethargy, cognitive fatigue, chronic dehydration are well-documented.
• Cyproterone Acetate (CPA): highly effective but severe meningioma risk at cumulative doses >36-60g (11-20x increased risk). French pharmacovigilance study 2020. Dose must be minimised (10-12.5 mg/day). Any neurological symptoms (vision loss, anosmia, tinnitus) = urgent MRI and stop CPA immediately.
• Bicalutamide: androgen receptor blocker. Does NOT lower serum T — can increase aromatization to E2. Washington University 2023 (n=84 AYA): no clinically significant hepatotoxicity at 50 mg. Emerging as safe alternative.
• Finasteride/Dutasteride: block DHT production. Useful for scalp hair preservation in both regimens. Generally very well tolerated. In transmasc individuals, they halt hair loss without counteracting virilization.

--- ESTROGEN DELIVERY ROUTES ---
• Oral: extensive first-pass liver metabolism → elevated clotting factors. VTE risk HR 1.9-4.3 vs transdermal. Monitoring limitation for supraphysiologic levels.
• Transdermal (patch/gel/spray): bypasses first-pass metabolism. No increased VTE risk. Preferred for age >50, smokers, cardiovascular risk. Stable continuous levels.
• Sublingual: direct mucosal absorption. 1mg sublingual achieves Cmax ~144 pg/mL vs ~35 pg/mL oral (same tablet). Short half-life — requires multi-daily dosing to maintain levels. Higher E2:E1 ratio.
• Injectable (EV, EC): extended dosing (1-2 weeks typically). Prone to supraphysiologic peaks followed by deep troughs (day 5-6) → mood swings, exhaustion. Best practice: start conservative (≤5 mg/week), titrate from trough labs.

--- PROGESTERONE ---
• Amsterdam UMC 2024 RCT: oral micronized progesterone + E2 = up to 30% more breast volume vs E2 alone (~1 cup size increase).
• Widely reported to improve sleep, libido, and mood — but clinical trial evidence is mixed. Pittsburgh Sleep Quality Index: no statistically significant improvement in RCT.
• Bioidentical progesterone does NOT carry the same cancer risk as synthetic progestins (medroxyprogesterone acetate).

--- THERAPEUTIC TARGETS ---
• Feminizing: Estradiol 100-200 pg/mL; Testosterone <50 ng/dL
• Masculinizing: Testosterone 300-1000 ng/dL; Estradiol <50 pg/mL
• Endocrine Society: avoid E2 >200 pg/mL to mitigate VTE risk. WPATH SOC8: targets are individualized based on patient goals and phenotypic response.

--- MENTAL HEALTH ---
• Stanford 2022: adolescents who accessed GAHT early had significantly lower depression, anxiety, and suicidality vs those who could not access treatment. GAHT is life-saving, not merely cosmetic.
• Overall: majority of patients report significant improvement in psychosocial wellbeing, dysphoria, depression, and anxiety after initiating GAHT.

=== APP SKILLS ===
You HAVE tools to perform these actions automatically! Do NOT tell the user to go do it themselves, DO IT FOR THEM by calling the tool when requested:
- Get their current graph data (E2/T levels)
- See what milestones they've checked off
- Read their blood test history
- Add or remove medications
- Log their daily mood and notes
- Toggle milestones on their behalf
- Add blood test results
- Search the web for medical info (this automatically provides a Sources link to the user)

=== YOUR PERSONALITY ===
You are Lily — warm, knowledgeable, non-judgmental, and deeply affirming. You celebrate wins, validate struggles, and ground all guidance in current evidence (WPATH SOC8, Endocrine Society). You always remind users to consult their clinician for dosage changes or medical decisions. You're concise but never cold.
"""

                        scope.launch {
                            try {
                                val toolHandler = object : GeminiClient.LilyToolHandler {
                                    override fun getGraphData(): String = viewModel.getSimulatedLevels()
                                    override fun getMilestonesStatus(): String = viewModel.getMilestonesStatus()
                                    override fun getBloodTestsHistory(): String = viewModel.getBloodTestsHistory()
                                    override fun addMedication(name: String, method: String, dose: String, frequency: String) {
                                        viewModel.addMedication(name, method, dose, frequency, false)
                                    }
                                    override fun removeMedication(name: String) {
                                        val med = medications.find { it.name.equals(name, ignoreCase = true) }
                                        if (med != null) viewModel.removeMedication(med.id)
                                    }
                                    override fun logToday(mood: String, notes: String) {
                                        viewModel.logToday(notes, mood, "")
                                    }
                                    override fun toggleMilestone(id: String, isChecked: Boolean) {
                                        viewModel.toggleMilestone(id, isChecked)
                                    }
                                    override fun addBloodTest(e2: Float, t: Float) {
                                        viewModel.addBloodTest(System.currentTimeMillis(), e2, t)
                                    }
                                }

                                val (responseText, finalSearchQuery) = GeminiClient.generateInsight(
                                    prompt = userMsg,
                                    systemInstruction = system,
                                    useNano = userProfile!!.useOnDeviceAi,
                                    providedApiKey = userProfile!!.apiKey,
                                    modelName = userProfile!!.selectedAiModel,
                                    toolHandler = toolHandler
                                )
                                viewModel.addChatMessage(responseText, isUser = false, sourcesQuery = finalSearchQuery)
                            } catch (e: Exception) {
                                viewModel.addChatMessage("Error connecting to AI: ${e.message}", isUser = false)
                            } catch (e: Throwable) {
                                viewModel.addChatMessage("Fatal AI error: ${e.message}", isUser = false)
                            }
                            isLoading = false
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
