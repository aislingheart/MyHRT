package com.example.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null
)

@Serializable
data class Tool(
    val googleSearch: JsonObject? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-pro-latest:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun generateInsight(prompt: String, systemInstruction: String = "You are a supportive, medically-accurate assistant for users on HRT (Hormone Replacement Therapy). Give concise, empathetic responses.", useNano: Boolean = false, providedApiKey: String? = null): String = withContext(Dispatchers.IO) {
        if (useNano) {
            try {
                // Warning: The public Gemini SDK does not natively support AICore without Google AI Edge packages, 
                // but if using the experimental client, it requires a dummy key.
                val nanoModel = com.google.ai.client.generativeai.GenerativeModel(
                    modelName = "gemini-nano", 
                    apiKey = "dummy_key_for_nano",
                    systemInstruction = com.google.ai.client.generativeai.type.content { text(systemInstruction) }
                )
                val response = nanoModel.generateContent(prompt)
                return@withContext response.text ?: "Local Nano model returned an empty response."
            } catch (e: Exception) {
                return@withContext "Gemini Nano is not available on this device (requires AICore supported hardware like Pixel 8 Pro). Error: ${e.message}"
            }
        }
        
        val apiKey = providedApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Please configure your Gemini API Key in Settings to receive dynamic insights."
        }
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            tools = listOf(Tool(googleSearch = JsonObject(emptyMap())))
        )
        try {
            val response = service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No insight available at this time."
        } catch (e: Exception) {
            "Unable to generate insight right now. Ensure your API key is correct. Error: ${e.message}"
        }
    }
}
