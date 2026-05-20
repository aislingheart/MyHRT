package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Pharmacokinetics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpectedEffectsScreen(viewModel: HRTViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val bloodTests by viewModel.bloodTests.collectAsStateWithLifecycle()
    val milestoneChecks by viewModel.milestoneChecks.collectAsStateWithLifecycle()
    val isFeminizing = profile?.regimenType == "Feminizing"

    // Calculate Average Area Under Curve for the Next 14 days calibration
    val avgLevel = remember(medications, bloodTests) {
        var sum = 0f
        val days = 14
        for (i in 0..days * 10) {
            sum += Pharmacokinetics.calculateConcentration(medications, i / 10.0, bloodTests).toFloat()
        }
        val avg = sum / (days * 10 + 1)
        avg
    }

    // Determine deficit ratio for timeline stretching
    val targetMin = if (isFeminizing) 100f else 300f
    
    // Stretch ratio cap: if average is strictly 0 (no meds), stretch is infinite, we cap it.
    // If levels are perfectly on target or above, ratio = 1.0 (no stretch).
    // If lower, stretch timelines by up to 3x.
    val stretchRatio = remember(avgLevel) {
        if (avgLevel >= targetMin) 1.0f
        else if (avgLevel <= 0.0f) 2.0f // Moderate stretch if no meds or zero tracking
        else (targetMin / avgLevel).coerceAtMost(1.5f) // Cap maximum stretching at 1.5x
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Expected Effects & Levels",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Pharmacokinetic Simulation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Estimated hormone accumulation over the next 14 days based on your medication history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val targetColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)

                    Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            
                            val daysPast = 30
                            val daysFuture = 14
                            val totalDays = daysPast + daysFuture
                            val points = remember(medications) { mutableListOf<Float>() }
                            var maxVal by remember(medications) { mutableStateOf(10.0f) }
                            
                            LaunchedEffect(medications) {
                                points.clear()
                                var localMax = 10.0f
                                val profileStartMillis = profile?.startDateMillis
                                for (i in -daysPast * 10..daysFuture * 10) { 
                                    val level = Pharmacokinetics.calculateConcentration(medications, i / 10.0, bloodTests, profileStartMillis)
                                    points.add(level.toFloat())
                                    if (level > localMax) localMax = level.toFloat()
                                }
                                maxVal = localMax
                            }

                            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                if (points.isEmpty()) return@Canvas
                                
                                val width = size.width
                                val height = size.height

                                // Draw reference grid lines
                                drawLine(color = Color.Gray.copy(alpha = 0.5f), start = Offset(0f, height), end = Offset(width, height), strokeWidth = 2f) // X Axis
                                drawLine(color = Color.Gray.copy(alpha = 0.5f), start = Offset(0f, 0f), end = Offset(0f, height), strokeWidth = 2f) // Y Axis
                                
                                // Draw Today line
                                val todayX = (daysPast.toFloat() / totalDays.toFloat()) * width
                                drawLine(
                                    color = primaryColor.copy(alpha=0.5f),
                                    start = Offset(todayX, 0f),
                                    end = Offset(todayX, height),
                                    strokeWidth = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )

                                val targetMax = if (isFeminizing) 200f else 1000f
                                
                                val yMin = height - (targetMin / maxVal * height)
                                val yMax = height - (targetMax / maxVal * height)
                                drawRect(
                                    color = targetColor,
                                    topLeft = Offset(0f, yMax),
                                    size = androidx.compose.ui.geometry.Size(width, yMin - yMax)
                                )

                                val path = Path()
                                points.forEachIndexed { index, value ->
                                    val x = (index.toFloat() / ((totalDays) * 10f)) * width
                                    val y = height - (value / maxVal * height)
                                    if (index == 0) path.moveTo(x, y)
                                    else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = primaryColor,
                                    style = Stroke(width = 4f)
                                )
                            }
                            
                            // Axis labels
                            Text("${maxVal.toInt()}", modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("0", modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("-30d", modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("Today", modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = (30.0/44.0 * 300).dp /* approximation */, bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("+14d", modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text("Target Hormone Levels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            if (isFeminizing) {
                                Text("• Estradiol (E2): 100 - 200 pg/mL (Trough)")
                                Text("• Testosterone (T): < 50 ng/dL")
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Note: Levels vary by administration method. Injections provide higher peaks, while transdermal and steady oral doses maintain flatter curves.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("• Testosterone (T): 300 - 1000 ng/dL (Trough)")
                                Text("• Estradiol (E2): < 50 pg/mL")
                            }
                        }
                    }
                }

                item {
                    Text("Dynamic Milestone Forecast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (stretchRatio > 1.0f) {
                        Text(
                            "Your estimated average lies below the therapeutic threshold. Deadlines below are algorithmically stretched based on your current cumulative exposure (AUC).",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Optimal dosing achieved! Timelines represent optimal expected onset.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val timelines = if (isFeminizing) femEffects else mascEffects
                items(timelines, key = { it.title }) { effect ->
                    val check = milestoneChecks.find { it.milestoneId == effect.title }
                    val isChecked = check != null
                    
                    var showPicker by remember { mutableStateOf(false) }
                    if (showPicker) {
                        val dateState = rememberDatePickerState()
                        DatePickerDialog(
                            onDismissRequest = { showPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    dateState.selectedDateMillis?.let { date ->
                                        viewModel.toggleMilestone(effect.title, true, date)
                                    }
                                    showPicker = false
                                }) { Text("OK") }
                            }
                        ) {
                            DatePicker(state = dateState)
                        }
                    }
                    
                    Card(modifier = Modifier.fillMaxWidth().animateItem(fadeInSpec = androidx.compose.animation.core.tween(500))) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(effect.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                                
                                val adjustedOnset = adjustTimelineString(effect.onset, stretchRatio)
                                val adjustedMax = adjustTimelineString(effect.maximum, stretchRatio)
                                
                                Text("Onset: $adjustedOnset | Maximum: $adjustedMax", color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(4.dp))
                                Text(effect.description, style = MaterialTheme.typography.bodyMedium)
                                
                                if (isChecked) {
                                    val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(check!!.achievedDateMillis))
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text("Achieved: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        TextButton(onClick = { showPicker = true }) { Text("Edit Date") }
                                    }
                                }
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { 
                                    if (it) showPicker = true
                                    else viewModel.toggleMilestone(effect.title, false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun adjustTimelineString(baseStr: String, stretch: Float): String {
    if (baseStr == "Unknown" || stretch <= 1.05f) return baseStr
    
    val regex = Regex("(\\d+)(?:-(\\d+))?\\s*(months?|years?)")
    val match = regex.find(baseStr) ?: return baseStr
    
    val group1 = match.groupValues[1].toFloatOrNull() ?: return baseStr
    val group2 = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toFloatOrNull()
    val unit = match.groupValues[3]
    
    val newStart = "%.1f".format(group1 * stretch).removeSuffix(".0")
    if (group2 != null) {
        val newEnd = "%.1f".format(group2 * stretch).removeSuffix(".0")
        return "$newStart-$newEnd $unit"
    } else {
        return "$newStart $unit"
    }
}

data class EffectTimeline(val title: String, val onset: String, val maximum: String, val description: String)

val femEffects = listOf(
    EffectTimeline("Emotional Changes & Psychological Relief", "1-3 months", "3-6 months", "Initial mental shifts, mood stabilization, or changes in how emotions are experienced and processed."),
    EffectTimeline("Changes in Perspiration & Odor", "1-3 months", "3-6 months", "Reduction in sweating and a shift from a 'musky' or typical male body odor to a softer or completely different scent."),
    EffectTimeline("Decreased Sexual Desire & Erections", "1-3 months", "3-6 months", "Reduction in spontaneous erectile function and changes in libido."),
    EffectTimeline("Halt of Androgenic Alopecia", "1-3 months", "1-2 years", "Halting or slight reversal of male-pattern baldness."),
    EffectTimeline("Skin Softening & Decreased Oil", "3-6 months", "Unknown", "Significant decreases in sebum production leading to less oily, softer, and more translucent skin."),
    EffectTimeline("Breast Budding", "3-6 months", "3-5 years", "Initial breast budding, leading to maximal breast tissue development around 3-5 years. (Bone structure like height/larynx does not change)."),
    EffectTimeline("Decreased Testicular Volume", "3-6 months", "2-3 years", "Reduction in testicular size, often by up to 50%, alongside decreased sperm production (which can become permanent)."),
    EffectTimeline("Early Fat Redistribution", "3-6 months", "3-5 years", "Beginning of subcutaneous fat moving toward the lower body (hips, thighs). Full realization in 3-5 years."),
    EffectTimeline("Decreased Muscle Mass", "3-6 months", "1-2 years", "Gradual reduction in upper body strength and muscle volume in the arms, chest, and shoulders."),
    EffectTimeline("Facial Fat Changes", "6-12 months", "2-5 years", "Softening of facial features through subcutaneous fat changes on the cheeks and jawline."),
    EffectTimeline("Body Hair Thinning", "6-12 months", "3 years", "Noticeable thinning and significant slowing of terminal facial and body hair growth."),
    EffectTimeline("Areola & Nipple Development", "1-2 years", "3-5 years", "Expansion and potential darkening of the areola and overall maturation of breast structure."),
    EffectTimeline("Decrease in Shoe & Band Size", "1-2 years", "2-3 years", "Loss of muscle/cartilage and ligament changes can lead to slightly smaller feet or decreased ribcage circumfrence."),
    EffectTimeline("Pelvic Tilt & Posture Changes", "1-2 years", "Unknown", "Changes in ligaments can cause anterior pelvic tilt, enhancing the curve of the lower back and resting posture.")
)

val mascEffects = listOf(
    EffectTimeline("Emotional Changes & Libido Increase", "1-3 months", "3-6 months", "Significant increase in sexual desire and potential shifts in emotional processing or mood stability."),
    EffectTimeline("Changes in Body Odor", "1-3 months", "3-6 months", "Shift toward a stronger or distinctly 'male' body odor and increased perspiration."),
    EffectTimeline("Cessation of Menstrual Cycle", "1-6 months", "1 year", "Periods typically lighten and then stop entirely."),
    EffectTimeline("Skin Oiliness & Acne", "1-6 months", "1-2 years", "Heavily increased oil production; acne may develop or worsen significantly."),
    EffectTimeline("Clitoral Enlargement", "1-6 months", "1-2 years", "Initiation of bottom growth which continues over the first few years, typically halting 2 years in."),
    EffectTimeline("Voice Deepening & Breaking", "3-6 months", "1-5 years", "Permanent thickening of vocal cords. Starts with cracking or breaking, absolute maximum realization by 1-5 years."),
    EffectTimeline("Vaginal/Frontal Atrophy", "3-6 months", "1-2 years", "Thinning of the vaginal lining, potentially causing dryness, cramping, or discomfort."),
    EffectTimeline("Fat Redistribution", "6-12 months", "2-5 years", "Rapid shifting of body fat toward the abdominal region and away from the hips/thighs."),
    EffectTimeline("Facial & Body Hair Growth", "6-12 months", "3-5 years", "Significant increases in terminal facial and body hair growth (chest, stomach, back, beard)."),
    EffectTimeline("Increased Muscle Mass", "6-12 months", "2-5 years", "Measurable increases in total muscle mass and upper body strength."),
    EffectTimeline("Hairline Recession", "1-5 years", "Unknown", "Frontal and temporal hairline recession, potential male pattern baldness based on genetics."),
    EffectTimeline("Vocal Pitch Settling", "1-2 years", "3-5 years", "Voice mostly stops breaking and settles into a permanently lower pitch and resonance."),
    EffectTimeline("Facial Bone Restructuring", "2-5 years", "Unknown", "Subtle broadening of jaw and cartilage changes, along with facial fat loss leading to firmer, more angular structures.")
)
