package com.example.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified Gemini client supporting:
 * 1. Cloud API via official Google Generative AI SDK
 * 2. On-device AICore (Gemini Nano) with graceful fallback to rule-based offline engine
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    // ──────────────────────────────────────────────
    // On-Device / Offline Rule-Based Engine
    // ──────────────────────────────────────────────

    private fun generateOfflineResponse(prompt: String, systemInstruction: String): String {
        val query = prompt.lowercase()
        return when {
            query.contains("breast") || query.contains("bud") ||
                    (query.contains("grow") && (query.contains("chest") || query.contains("breast"))) -> {
                "**Local Estimate — Breast Growth:**\n" +
                "• **Onset:** Typically starts within 2-3 months on feminizing HRT regimens.\n" +
                "• **Peak Development:** Usually takes 2-5 years to reach maximum size and development.\n" +
                "• **Mechanism:** Budding starts with breast buds under the nipples, followed by gradual redistribution of fat and glandular tissue enlargement.\n\n" +
                "*Tip: Keeping your Estradiol levels stable helps support natural, healthy development. Gentle massage can relieve initial tenderness.*"
            }
            query.contains("voice") || query.contains("pitch") || query.contains("vocal") || query.contains("deep") -> {
                "**Local Estimate — Voice Deepening:**\n" +
                "• **Onset:** Usually begins around 3-12 months on masculinizing HRT regimens (Testosterone).\n" +
                "• **Peak Effect:** Achieved in 1-2 years as vocal cords thicken permanently.\n" +
                "• **Note:** Voice deepening on Testosterone is generally permanent. On feminizing HRT, natural voice pitch does not change; vocal training is typically recommended to raise talking resonance."
            }
            query.contains("hair") || query.contains("facial") || query.contains("shave") || query.contains("beard") -> {
                "**Local Estimate — Hair Growth and Density:**\n" +
                "• **Feminizing HRT:** Facial and body hair slow down in growth speed and become softer, finer, and lighter over 6-12 months.\n" +
                "• **Masculinizing HRT:** Coarse, dark terminal hairs (facial hair, chest/abdominals) start growing around 3-12 months and continue maturing for up to 5 years."
            }
            query.contains("fat") || query.contains("hips") || query.contains("muscle") || query.contains("weight") -> {
                "**Local Estimate — Body Composition:**\n" +
                "• **Fat Shifts:** Dynamic redistribution of subcutaneous fat occurs over 3-6 months (shifting to hips/thighs/glutes for feminizing, or midsection/abdominal areas for masculinizing regimens).\n" +
                "• **Muscle Mass:** High level of change beginning after 3 months. Strength adapts with appropriate physical training."
            }
            query.contains("mood") || query.contains("emotional") || query.contains("depressed") || query.contains("happy") || query.contains("anxious") -> {
                "**Local Estimate — Mood and Emotions:**\n" +
                "• **Hormone Fluctuations:** Changing levels (especially prior to next dosing/troughs) might cause transient mood swings, anxiety, or fatigue.\n" +
                "• **Therapeutic Advice:** Keep Estradiol or Testosterone levels within stable target ranges to prevent mood drops. Tracking symptoms daily using the logs tab is recommended."
            }
            query.contains("skin") || query.contains("acne") || query.contains("oily") -> {
                "**Local Estimate — Skin Changes:**\n" +
                "• **Feminizing HRT:** Skin becomes softer, thinner, and less oily within 3-6 months. Pores may appear smaller.\n" +
                "• **Masculinizing HRT:** Increased oil production and acne are common in the first 6-12 months due to androgenic stimulation of sebaceous glands.\n" +
                "• **Tip:** A consistent skincare routine with gentle cleansers helps manage these transitions."
            }
            query.contains("libido") || query.contains("sex") || query.contains("sexual") || query.contains("desire") -> {
                "**Local Estimate — Libido & Sexual Function:**\n" +
                "• **Feminizing HRT:** Libido often decreases initially (first 1-3 months), then may stabilize at a lower baseline. Erections become less spontaneous.\n" +
                "• **Masculinizing HRT:** Libido typically increases significantly within the first 1-3 months. Clitoral sensitivity and growth occur early."
            }
            query.contains("period") || query.contains("menstrual") || query.contains("menses") || query.contains("bleeding") -> {
                "**Local Estimate — Menstrual Changes:**\n" +
                "• **Masculinizing HRT:** Menses typically cease (amenorrhea) within 2-6 months of testosterone therapy.\n" +
                "• Breakthrough bleeding can occur if doses are inconsistent.\n" +
                "• **Clinical Note:** If periods persist beyond 6 months on adequate T levels, consult your endocrinologist."
            }
            query.contains("hello") || query.contains("hi ") || query.contains("hey") || query.contains("who are you") -> {
                "Hello! I'm your offline HRT companion. Since we're running in offline mode, I provide clinical reference information based on published medical guidelines (WPATH SOC 8, Endocrine Society). For more detailed, personalized AI insights, set up your Gemini API Key in Settings."
            }
            else -> {
                "**On-Device Clinical Reference Profile:**\n" +
                "• **Target Estradiol (E2):** 100 - 200 pg/mL (Feminizing steady troughs support healthy changes).\n" +
                "• **Target Testosterone (T):** < 50 ng/dL (Feminizing), or 300 - 1000 ng/dL (Masculinizing physiologically).\n" +
                "• **Timelines:** Initial cell and oiliness/skin adaptations occur in weeks. Fat and shape redistributions take several years of cumulative safe exposure.\n\n" +
                "*Provide more specific keywords like 'breast', 'voice', 'hair', 'fat', 'skin', 'libido', or 'mood' to fetch detailed offline medication guides, or set up your Gemini API Key in Profile settings to unlock cloud-powered intelligence.*"
            }
        }
    }

    // ──────────────────────────────────────────────
    // On-Device AICore (Gemini Nano)
    // ──────────────────────────────────────────────

    /**
     * Attempts real on-device inference via AICore's GenerativeModel.
     * Returns null if AICore is not available on this device,
     * allowing the caller to fall back to the rule-based engine.
     */
    private suspend fun tryAiCoreGeneration(prompt: String, systemInstruction: String): String? {
        return try {
            val generationConfig = com.google.ai.edge.aicore.GenerationConfig.builder().build()
            val downloadConfig = com.google.ai.edge.aicore.DownloadConfig.builder().build()
            val model = com.google.ai.edge.aicore.GenerativeModel(generationConfig, downloadConfig)

            val fullPrompt = "$systemInstruction\n\nUser question: $prompt"
            val response = model.generateContent(fullPrompt)
            response.text?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "AICore not available on this device: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────
    // Cloud Gemini API via Official SDK (With Function Calling)
    // ──────────────────────────────────────────────

    interface LilyToolHandler {
        fun getGraphData(): String
        fun getMilestonesStatus(): String
        fun getBloodTestsHistory(): String
        fun addMedication(name: String, method: String, dose: String, frequency: String)
        fun removeMedication(name: String)
        fun logToday(mood: String, notes: String)
        fun toggleMilestone(id: String, isChecked: Boolean)
        fun addBloodTest(e2: Float, t: Float)
    }

    /**
     * Main entry point for generating AI content with function calling.
     * Returns a Pair containing the response text and an optional sources search query.
     */
    suspend fun generateInsight(
        prompt: String,
        systemInstruction: String = "You are a supportive assistant.",
        useNano: Boolean = false,
        providedApiKey: String? = null,
        modelName: String = "gemini-3.5-flash",
        toolHandler: LilyToolHandler? = null
    ): Pair<String, String?> = withContext(Dispatchers.IO) {

        if (useNano) {
            val aiCoreResponse = tryAiCoreGeneration(prompt, systemInstruction)
            if (aiCoreResponse != null) {
                return@withContext Pair("📱 [Gemini Nano — On-Device]:\n\n$aiCoreResponse", null)
            }
            val response = generateOfflineResponse(prompt, systemInstruction)
            return@withContext Pair("📱 [On-Device — Offline Mode]:\n\n$response", null)
        }

        val apiKey = providedApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Pair("Please configure your Gemini API Key in Settings to receive dynamic insights.", null)
        }

        try {
            // Define tools
            val getGraphDataTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "get_graph_data",
                description = "Get the current simulated hormone levels (E2 and T) from the graph based on the user's active medications and blood test calibration.",
                parameters = emptyList()
            )
            val getMilestonesTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "get_milestones",
                description = "Get the status of all available milestones (checked or unchecked) and their dates.",
                parameters = emptyList()
            )
            val getBloodTestsTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "get_blood_tests",
                description = "Get the user's complete history of logged blood test results.",
                parameters = emptyList()
            )
            val searchWebTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "search_web",
                description = "Use this to search the internet when you need external sources or recent medical information. This will present a 'Sources' button to the user.",
                parameters = listOf(
                    com.google.ai.client.generativeai.type.Schema.str("query", "The search query to look up on the web.")
                ),
                requiredParameters = listOf("query")
            )
            val logTodayTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "log_today",
                description = "Log the user's mood and journal notes for today.",
                parameters = listOf(
                    com.google.ai.client.generativeai.type.Schema.str("mood", "The user's mood (e.g., Happy, Sad, Anxious, Stable)"),
                    com.google.ai.client.generativeai.type.Schema.str("notes", "Journal notes or symptoms described")
                ),
                requiredParameters = listOf("mood", "notes")
            )
            val toggleMilestoneTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "toggle_milestone",
                description = "Check or uncheck a milestone to mark it as achieved or not achieved.",
                parameters = listOf(
                    com.google.ai.client.generativeai.type.Schema.str("id", "The exact title of the milestone"),
                    com.google.ai.client.generativeai.type.Schema.bool("isChecked", "True to mark achieved, false to unmark")
                ),
                requiredParameters = listOf("id", "isChecked")
            )
            val addMedicationTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "add_medication",
                description = "Add a new medication to the user's regimen.",
                parameters = listOf(
                    com.google.ai.client.generativeai.type.Schema.str("name", "Name of the medication (e.g. Estradiol Valerate)"),
                    com.google.ai.client.generativeai.type.Schema.str("method", "Administration method (e.g. Oral, Injection, Patch)"),
                    com.google.ai.client.generativeai.type.Schema.str("dose", "Dose (e.g. 2mg, 0.5ml)"),
                    com.google.ai.client.generativeai.type.Schema.str("frequency", "Frequency (e.g. Daily, Weekly)")
                ),
                requiredParameters = listOf("name", "method", "dose", "frequency")
            )
            val removeMedicationTool = com.google.ai.client.generativeai.type.defineFunction(
                name = "remove_medication",
                description = "Remove a medication from the user's regimen.",
                parameters = listOf(
                    com.google.ai.client.generativeai.type.Schema.str("name", "Name of the medication to remove")
                ),
                requiredParameters = listOf("name")
            )

            val tools = listOf(
                com.google.ai.client.generativeai.type.Tool(
                    listOf(getGraphDataTool, getMilestonesTool, getBloodTestsTool, searchWebTool, logTodayTool, toggleMilestoneTool, addMedicationTool, removeMedicationTool)
                )
            )

            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = content { text(systemInstruction) },
                tools = if (toolHandler != null) tools else null,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 8192
                }
            )

            var response = model.generateContent(prompt)
            var finalSearchQuery: String? = null

            // Handle function calls if any
            if (response.functionCalls.isNotEmpty() && toolHandler != null) {
                val toolResultsStr = java.lang.StringBuilder()
                toolResultsStr.append("You called tools and received the following results:\n")
                
                response.functionCalls.forEach { call ->
                    when (call.name) {
                        "get_graph_data" -> toolResultsStr.append("- get_graph_data: ${toolHandler.getGraphData()}\n")
                        "get_milestones" -> toolResultsStr.append("- get_milestones: ${toolHandler.getMilestonesStatus()}\n")
                        "get_blood_tests" -> toolResultsStr.append("- get_blood_tests: ${toolHandler.getBloodTestsHistory()}\n")
                        "log_today" -> {
                            val args = call.args
                            val mood = args["mood"] as? String ?: ""
                            val notes = args["notes"] as? String ?: ""
                            toolHandler.logToday(mood, notes)
                            toolResultsStr.append("- log_today: Logged successfully.\n")
                        }
                        "toggle_milestone" -> {
                            val args = call.args
                            val id = args["id"] as? String ?: ""
                            val isChecked = args["isChecked"] as? Boolean ?: false
                            toolHandler.toggleMilestone(id, isChecked)
                            toolResultsStr.append("- toggle_milestone: Milestone toggled successfully.\n")
                        }
                        "add_medication" -> {
                            val args = call.args
                            val name = args["name"] as? String ?: ""
                            val method = args["method"] as? String ?: ""
                            val dose = args["dose"] as? String ?: ""
                            val freq = args["frequency"] as? String ?: ""
                            toolHandler.addMedication(name, method, dose, freq)
                            toolResultsStr.append("- add_medication: Medication added successfully.\n")
                        }
                        "remove_medication" -> {
                            val args = call.args
                            val name = args["name"] as? String ?: ""
                            toolHandler.removeMedication(name)
                            toolResultsStr.append("- remove_medication: Attempted to remove medication.\n")
                        }
                        "search_web" -> {
                            val args = call.args
                            val query = args["query"] as? String ?: ""
                            finalSearchQuery = query
                            toolResultsStr.append("- search_web: Web search tool activated. Tell the user you have attached a Sources button for them to view the results for: $query\n")
                        }
                        else -> toolResultsStr.append("- ${call.name}: Unknown function\n")
                    }
                }
                toolResultsStr.append("\nPlease formulate your final response to the user based on these results.")

                // Create a final model instance WITHOUT tools to prevent infinite loops, 
                // and pass the tool results as part of the user prompt to bypass SDK history bugs
                val finalModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    systemInstruction = content { text(systemInstruction) },
                    tools = null,
                    generationConfig = generationConfig {
                        temperature = 0.7f
                        topK = 40
                        topP = 0.95f
                        maxOutputTokens = 8192
                    }
                )
                val newPrompt = "$prompt\n\n$toolResultsStr"
                response = finalModel.generateContent(newPrompt)
            }

            val responseText = response.text ?: "No insight available at this time."
            return@withContext Pair(responseText, finalSearchQuery)

        } catch (e: Exception) {
            Log.e(TAG, "Cloud API error", e)
            return@withContext Pair("Unable to generate insight right now. Ensure your API key is correct and the model '$modelName' is accessible. Error: ${e.message}", null)
        }
    }
}
