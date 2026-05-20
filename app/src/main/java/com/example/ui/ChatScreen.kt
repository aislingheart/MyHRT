package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.GeminiClient
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: HRTViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() } // Boolean true if user
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Assistant") }) }
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
                    Text("Ask me about your HRT journey, medications, or timeline expectations. I have access to your profile context.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                    items(messages.reversed()) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (msg.second) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.second) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(msg.first, modifier = Modifier.padding(12.dp))
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
                    placeholder = { Text("Ask something...") },
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = input.isNotBlank() && !isLoading,
                    onClick = {
                        val userMsg = input
                        input = ""
                        messages.add(userMsg to true)
                        isLoading = true
                        
                        val curMeds = medications.joinToString { "${it.name} ${it.dose}" }
                        val curLog = logs.firstOrNull()?.notes ?: "None"
                        val system = """You are Bloom's helpful, empathetic HRT assistant. 
                        Context: User is on ${userProfile!!.regimenType} HRT.
                        Started: ${java.util.Date(userProfile!!.startDateMillis)}.
                        Meds: $curMeds.
                        Recent Log: $curLog.
                        Respond concisely but helpfully based on medical literature (Endocrine Society, WPATH) and empathy."""

                        scope.launch {
                            try {
                                val response = GeminiClient.generateInsight(
                                    prompt = userMsg,
                                    systemInstruction = system,
                                    useNano = userProfile!!.useOnDeviceAi,
                                    providedApiKey = userProfile!!.apiKey
                                )
                                messages.add(response to false)
                            } catch (e: Exception) {
                                messages.add("Error connecting to AI: ${e.message}" to false)
                            } catch (e: Throwable) {
                                messages.add("Fatal AI error: ${e.message}" to false)
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
