package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Pharmacokinetics
import com.example.data.Medication
import com.example.data.BloodTestResult
import java.text.SimpleDateFormat
import java.util.*

data class SeriesPoint(
    val dayOffset: Double,
    val dateStr: String,
    val e2Val: Double,
    val tVal: Double,
    val otherVals: Map<String, Double>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpectedEffectsScreen(viewModel: HRTViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val bloodTests by viewModel.bloodTests.collectAsStateWithLifecycle()
    val milestoneChecks by viewModel.milestoneChecks.collectAsStateWithLifecycle()
    val isFeminizing = profile?.regimenType == "Feminizing"

    // Custom range selection state
    val ranges = listOf("1 Week", "1 Month", "3 Months", "6 Months", "1 Year")
    var selectedRange by remember { mutableStateOf("1 Month") }

    // Plotted series filters toggles
    var filterE2 by remember { mutableStateOf(true) }
    var filterT by remember { mutableStateOf(true) }
    
    // Dynamic list of other medications tracked
    val otherMedsList = remember(medications) {
        medications.filter { med ->
            !med.name.contains("Estradiol", true) &&
            !med.name.contains("Estrogen", true) &&
            !med.name.contains("E2", true) &&
            !med.name.contains("Testosterone", true) &&
            !med.name.contains("Androgel", true) &&
            !med.name.contains("Sustanon", true) &&
            !med.name.contains("T-Gel", true)
        }.map { it.name }.distinct()
    }
    
    // State of active other drug toggles (starts enabled for all available)
    var selectedOtherFilters by remember(otherMedsList) {
        mutableStateOf(otherMedsList.toSet())
    }

    // Days past/future configurations
    val rangeConfig = remember(selectedRange) {
        when (selectedRange) {
            "1 Week" -> Pair(5, 2)
            "1 Month" -> Pair(20, 10)
            "3 Months" -> Pair(60, 30)
            "6 Months" -> Pair(120, 60)
            "1 Year" -> Pair(240, 120)
            else -> Pair(20, 10)
        }
    }
    val daysPast = rangeConfig.first
    val daysFuture = rangeConfig.second
    val totalDays = daysPast + daysFuture

    // Step optimizer to keep drawing ultra-fast and lag-free for large scales
    val step = remember(totalDays) {
        when {
            totalDays <= 10 -> 0.1
            totalDays <= 45 -> 0.2
            totalDays <= 120 -> 0.5
            else -> 1.0
        }
    }

    // Pre-calculate full time series data
    val seriesPoints = remember(medications, bloodTests, profile, daysPast, daysFuture, step) {
        val list = mutableListOf<SeriesPoint>()
        val startMillis = profile?.startDateMillis
        val now = System.currentTimeMillis()
        val stepsCount = (totalDays / step).toInt()
        
        for (i in 0..stepsCount) {
            val dayOffset = -daysPast + (i * step)
            val dateMs = now + (dayOffset * 86400000L).toLong()
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateMs))
            
            val e2 = Pharmacokinetics.calculateEstradiol(medications, dayOffset, bloodTests, startMillis, isFeminizing)
            val t = Pharmacokinetics.calculateTestosterone(medications, dayOffset, bloodTests, startMillis, isFeminizing, e2)
            
            val otherVals = mutableMapOf<String, Double>()
            medications.forEach { med ->
                if (!med.name.contains("Estradiol", true) &&
                    !med.name.contains("Estrogen", true) &&
                    !med.name.contains("E2", true) &&
                    !med.name.contains("Testosterone", true) &&
                    !med.name.contains("Androgel", true) &&
                    !med.name.contains("Sustanon", true) &&
                    !med.name.contains("T-Gel", true)) {
                    otherVals[med.name] = Pharmacokinetics.calculateOtherDrug(med, dayOffset, startMillis)
                }
            }
            list.add(SeriesPoint(dayOffset, dateStr, e2, t, otherVals))
        }
        list
    }

    // Determine values scale limits for rendering
    val maxE2 = remember(seriesPoints) { (seriesPoints.maxOfOrNull { it.e2Val } ?: 10.0).toFloat().coerceAtLeast(10f) }
    val maxT = remember(seriesPoints) { (seriesPoints.maxOfOrNull { it.tVal } ?: 10.0).toFloat().coerceAtLeast(10f) }
    
    val otherMaxes = remember(seriesPoints, otherMedsList) {
        otherMedsList.associateWith { name ->
            (seriesPoints.maxOfOrNull { it.otherVals[name] ?: 0.0 } ?: 10.0).toFloat().coerceAtLeast(10f)
        }
    }

    // Current real-time level computations for the top card (Day 0 / Today)
    val currentE2 = remember(medications, bloodTests, profile) {
        Pharmacokinetics.calculateEstradiol(medications, 0.0, bloodTests, profile?.startDateMillis, isFeminizing)
    }
    val currentT = remember(medications, bloodTests, profile) {
        Pharmacokinetics.calculateTestosterone(medications, 0.0, bloodTests, profile?.startDateMillis, isFeminizing, currentE2)
    }
    val otherCurrents = remember(medications, profile) {
        medications.filter { med ->
            !med.name.contains("Estradiol", true) &&
            !med.name.contains("Estrogen", true) &&
            !med.name.contains("E2", true) &&
            !med.name.contains("Testosterone", true) &&
            !med.name.contains("Androgel", true) &&
            !med.name.contains("Sustanon", true) &&
            !med.name.contains("T-Gel", true)
        }.associate { med ->
            med.name to Pharmacokinetics.calculateOtherDrug(med, 0.0, profile?.startDateMillis)
        }
    }

    // Interactive scrubbing states
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    
    // Find scrubbed point
    val activePointIndex = remember(scrubFraction, seriesPoints) {
        scrubFraction?.let { frac ->
            (frac * (seriesPoints.size - 1)).toInt().coerceIn(0, seriesPoints.size - 1)
        }
    }
    val activePoint = remember(activePointIndex, seriesPoints) {
        activePointIndex?.let { seriesPoints[it] }
    }

    // Deficit Ratio & Stretched Timelines Calculations
    val avgLevel = remember(medications, bloodTests) {
        var sum = 0f
        val days = 14
        for (i in 0..days * 10) {
            sum += Pharmacokinetics.calculateConcentration(medications, i / 10.0, bloodTests).toFloat()
        }
        sum / (days * 10 + 1)
    }
    val targetMin = if (isFeminizing) 100f else 300f
    val stretchRatio = remember(avgLevel) {
        if (avgLevel >= targetMin) 1.0f
        else if (avgLevel <= 0.0f) 2.0f
        else (targetMin / avgLevel).coerceAtMost(1.5f)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Expected Effects & Levels",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Explore your hormone curves, active medications, and milestones in one unified view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Current Circulating Levels (TOP CARD)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Your Current Active Levels",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Estradiol (E2)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text(
                                    text = "%.1f pg/mL".format(currentE2),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Testosterone (T)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text(
                                    text = "%.1f ng/dL".format(currentT),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        if (otherCurrents.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            Spacer(Modifier.height(12.dp))
                            Text("Other Active Medications:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(4.dp))
                            otherCurrents.forEach { (name, level) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("%.1f mg equiv.".format(level), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Chart Controls Card
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Interactive Levels Simulation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Drag your finger/mouse across the graph to scrub the timeline and inspect medical estimates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // Range Selection Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ranges.forEach { range ->
                            FilterChip(
                                selected = selectedRange == range,
                                onClick = { 
                                    selectedRange = range
                                    scrubFraction = null
                                },
                                label = { Text(range) },
                                leadingIcon = {
                                    if (selectedRange == range) {
                                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }

                    // Plot Series Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterE2,
                            onClick = { filterE2 = !filterE2 },
                            label = { Text("E2 (Estradiol)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF06292).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFFC2185B)
                            )
                        )
                        FilterChip(
                            selected = filterT,
                            onClick = { filterT = !filterT },
                            label = { Text("Testosterone (T)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF26A69A).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFF00796B)
                            )
                        )
                        otherMedsList.forEach { name ->
                            val isSelected = selectedOtherFilters.contains(name)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedOtherFilters = if (isSelected) {
                                        selectedOtherFilters - name
                                    } else {
                                        selectedOtherFilters + name
                                    }
                                },
                                label = { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFBA68C8).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFF7B1FA2)
                                )
                            )
                        }
                    }
                }
            }

            // Simulation Graph Card with Custom Graph Drawing & Touch Scrubbing
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val strokeE2 = Color(0xFFEC407A)
                        val strokeT = Color(0xFF26A69A)
                        val strokeOther = Color(0xFFAB47BC)
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .pointerInput(seriesPoints, totalDays) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val position = event.changes.firstOrNull()?.position
                                            val pressed = event.changes.any { it.pressed }
                                            if (pressed && position != null) {
                                                scrubFraction = (position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            } else {
                                                scrubFraction = null
                                            }
                                        }
                                    }
                                }
                        ) {
                            if (seriesPoints.isEmpty()) return@Canvas

                            val width = size.width
                            val height = size.height

                            // Draw reference grid lines
                            drawLine(color = gridColor, start = Offset(0f, height), end = Offset(width, height), strokeWidth = 2f)
                            drawLine(color = gridColor, start = Offset(0f, 0f), end = Offset(0f, height), strokeWidth = 2f)
                            
                            // Horizontal grid intervals
                            val gridLinesCount = 4
                            for (c in 1..gridLinesCount) {
                                val yGrid = height * (c.toFloat() / (gridLinesCount + 1))
                                drawLine(
                                    color = gridColor.copy(alpha = 0.25f),
                                    start = Offset(0f, yGrid),
                                    end = Offset(width, yGrid),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                            }

                            // 'Today' (Day 0) vertical position
                            val todayX = (daysPast.toFloat() / totalDays.toFloat()) * width
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(todayX, 0f),
                                end = Offset(todayX, height),
                                strokeWidth = 2.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                            )

                            // 1. Draw Estradiol (E2) Series
                            if (filterE2) {
                                val path = Path()
                                seriesPoints.forEachIndexed { index, pt ->
                                    val x = (index.toFloat() / (seriesPoints.size - 1)) * width
                                    val y = height - (pt.e2Val.toFloat() / maxE2 * height)
                                    if (index == 0) path.moveTo(x, y)
                                    else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeE2,
                                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                                )
                            }

                            // 2. Draw Testosterone (T) Series
                            if (filterT) {
                                val path = Path()
                                seriesPoints.forEachIndexed { index, pt ->
                                    val x = (index.toFloat() / (seriesPoints.size - 1)) * width
                                    val y = height - (pt.tVal.toFloat() / maxT * height)
                                    if (index == 0) path.moveTo(x, y)
                                    else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeT,
                                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                                )
                            }

                            // 3. Draw Selected Other Medications
                            selectedOtherFilters.forEach { drugName ->
                                val maxVal = otherMaxes[drugName] ?: 10f
                                val path = Path()
                                seriesPoints.forEachIndexed { index, pt ->
                                    val x = (index.toFloat() / (seriesPoints.size - 1)) * width
                                    val y = height - ((pt.otherVals[drugName] ?: 0.0).toFloat() / maxVal * height)
                                    if (index == 0) path.moveTo(x, y)
                                    else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeOther,
                                    style = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f)))
                                )
                            }

                            // 4. Draw Interactive Scrubbing Vertical Line & Intersection Points
                            scrubFraction?.let { frac ->
                                val cursorX = frac * width
                                
                                // Cursor bar
                                drawLine(
                                    color = primaryColor,
                                    start = Offset(cursorX, 0f),
                                    end = Offset(cursorX, height),
                                    strokeWidth = 2f
                                )

                                activePointIndex?.let { idx ->
                                    val pt = seriesPoints.getOrNull(idx)
                                    if (pt != null) {
                                        if (filterE2) {
                                            val yE2 = height - (pt.e2Val.toFloat() / maxE2 * height)
                                            drawCircle(color = strokeE2, radius = 6.dp.toPx(), center = Offset(cursorX, yE2))
                                            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(cursorX, yE2))
                                        }
                                        if (filterT) {
                                            val yT = height - (pt.tVal.toFloat() / maxT * height)
                                            drawCircle(color = strokeT, radius = 6.dp.toPx(), center = Offset(cursorX, yT))
                                            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(cursorX, yT))
                                        }
                                        selectedOtherFilters.forEach { drugName ->
                                            val maxVal = otherMaxes[drugName] ?: 10f
                                            val valOther = pt.otherVals[drugName] ?: 0.0
                                            val yO = height - (valOther.toFloat() / maxVal * height)
                                            drawCircle(color = strokeOther, radius = 5.dp.toPx(), center = Offset(cursorX, yO))
                                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(cursorX, yO))
                                        }
                                    }
                                }
                            }
                        }

                        // Static labels inside graph
                        Text(
                            text = "-$daysPast d",
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "+$daysFuture d",
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Today",
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Real-time Tooltip Panel with scrubbed or current values
            item {
                val pt = activePoint ?: SeriesPoint(
                    dayOffset = 0.0,
                    dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
                    e2Val = currentE2,
                    tVal = currentT,
                    otherVals = otherCurrents
                )

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activePoint != null) "Inspected Values" else "Current Baseline (Today)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pt.dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        val relativeDays = pt.dayOffset.toInt()
                        val relativeStr = when {
                            relativeDays == 0 -> "Today"
                            relativeDays > 0 -> "In $relativeDays days"
                            else -> "${-relativeDays} days ago"
                        }
                        Text(
                            text = "Day offset: $relativeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (filterE2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Circle, contentDescription = null, tint = Color(0xFFEC407A), modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Estradiol (E2)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("%.1f pg/mL".format(pt.e2Val), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (filterT) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Circle, contentDescription = null, tint = Color(0xFF26A69A), modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Testosterone (T)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("%.1f ng/dL".format(pt.tVal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            selectedOtherFilters.forEach { name ->
                                val valOther = pt.otherVals[name] ?: 0.0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Circle, contentDescription = null, tint = Color(0xFFAB47BC), modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("%.1f mg equiv.".format(valOther), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Ideal Target Guidelines Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Therapeutic Target Guidelines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (isFeminizing) {
                            Text("• Estradiol (E2): 100 - 200 pg/mL (Flatter steady troughs help physical changes)")
                            Text("• Testosterone (T): < 50 ng/dL (Sufficient blockade prevents masculinization)")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Note: Troughs are the lowest level right before you take your next medication dose. Maintaining a stable level protects against mood swings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        } else {
                            Text("• Testosterone (T): 300 - 1000 ng/dL (Healthy male physiological baseline)")
                            Text("• Estradiol (E2): < 50 pg/mL (Aromatic suppression decreases unnecessary levels)")
                        }
                    }
                }
            }

            // Timeline stretch warn/success label
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
                Spacer(Modifier.height(4.dp))
            }

            // Render interactive timeline elements
            val timelines = if (isFeminizing) femEffects else mascEffects
            items(timelines, key = { it.title }) { effect ->
                val check = milestoneChecks.find { it.milestoneId == effect.title }
                val isChecked = check != null
                
                var showOptionsDialog by remember { mutableStateOf(false) }
                var showDatePicker by remember { mutableStateOf(false) }
                
                if (showOptionsDialog) {
                    AlertDialog(
                        onDismissRequest = { showOptionsDialog = false },
                        title = { Text("When was this achieved?") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val startMillis = profile?.startDateMillis ?: System.currentTimeMillis()
                                val options = listOf(
                                    Pair("At Onset", startMillis),
                                    Pair("1 Month Mark", startMillis + 30L * 24 * 60 * 60 * 1000),
                                    Pair("3 Month Mark", startMillis + 90L * 24 * 60 * 60 * 1000),
                                    Pair("6 Month Mark", startMillis + 180L * 24 * 60 * 60 * 1000),
                                    Pair("1 Year Mark", startMillis + 365L * 24 * 60 * 60 * 1000)
                                )
                                options.forEach { (label, dateMs) ->
                                    TextButton(
                                        onClick = {
                                            viewModel.toggleMilestone(effect.title, true, dateMs)
                                            showOptionsDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                HorizontalDivider()
                                TextButton(
                                    onClick = {
                                        showDatePicker = true
                                        showOptionsDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Specify Exact Date...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showOptionsDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                if (showDatePicker) {
                    val dateState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dateState.selectedDateMillis?.let { date ->
                                    viewModel.toggleMilestone(effect.title, true, date)
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        }
                    ) {
                        DatePicker(state = dateState)
                    }
                }
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = effect.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            )
                            
                            val adjustedOnset = adjustTimelineString(effect.onset, stretchRatio)
                            val adjustedMax = adjustTimelineString(effect.maximum, stretchRatio)
                            
                            Text("Onset: $adjustedOnset | Maximum: $adjustedMax", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(effect.description, style = MaterialTheme.typography.bodyMedium)
                            
                            if (isChecked) {
                                val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(check!!.achievedDateMillis))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Achieved: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { showOptionsDialog = true }) { Text("Edit Date") }
                                }
                            }
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { 
                                if (it) showOptionsDialog = true
                                else viewModel.toggleMilestone(effect.title, false)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Timeline effect structures & helpers for dynamically estimated onset/maximum durations
data class TimelineEffect(
    val title: String,
    val onset: String,
    val maximum: String,
    val description: String,
    val reversibility: String = "Variable",
    val researchNote: String = ""
)

val femEffects = listOf(
    TimelineEffect(
        title = "Decreased Libido & Erection Changes",
        onset = "1-3 months",
        maximum = "3-6 months",
        description = "Reduced sexual interest is typically the first noticed change. Spontaneous and morning erections decrease significantly in frequency and firmness as testosterone drops into female physiological ranges (< 50 ng/dL).",
        reversibility = "Reversible",
        researchNote = "Endocrine Society guidelines: onset within weeks of achieving adequate androgen suppression."
    ),
    TimelineEffect(
        title = "Cessation of Male Pattern Balding",
        onset = "1-3 months",
        maximum = "1-2 years",
        description = "Estradiol and androgen suppression halt DHT-driven follicular miniaturization. Hair loss stops progressing and some regrowth may occur at the temples and crown. 5-alpha reductase inhibitors (finasteride/dutasteride) can accelerate regrowth by blocking DHT conversion.",
        reversibility = "Reversible (halting), variable regrowth",
        researchNote = "WPATH SOC8: finasteride/dutasteride are highly complementary adjuncts for scalp hair preservation."
    ),
    TimelineEffect(
        title = "Softening of Skin & Reduced Oiliness",
        onset = "3-6 months",
        maximum = "Unknown (ongoing)",
        description = "Estradiol acts on sebaceous glands to reduce oil production — skin becomes softer, pores appear smaller, and overall texture becomes smoother. Body odor also changes character as sweat gland activity shifts. One of the most consistently reported early effects.",
        reversibility = "Reversible",
        researchNote = "Endocrine Society timeline table: onset 3-6 months; maximum unknown/ongoing."
    ),
    TimelineEffect(
        title = "Breast Development (Thelarche)",
        onset = "3-6 months",
        maximum = "2-3 years",
        description = "Development begins with tender, firm breast buds forming beneath the areola — identical to natal female puberty (Tanner stages). Nipple and areola enlarge; glandular and lobular tissue expands over years. A 2024 Amsterdam UMC RCT confirmed adding micronized progesterone increased breast volume by up to 30% (~1 cup size). UCSF and Endocrine Society recommend waiting 1-2 years before augmentation surgery.",
        reversibility = "Irreversible",
        researchNote = "Amsterdam UMC 2024 RCT (n=90): progesterone arm showed statistically significant volumetric increase vs control."
    ),
    TimelineEffect(
        title = "Body Fat Redistribution",
        onset = "3-6 months",
        maximum = "2-5 years",
        description = "Subcutaneous fat migrates away from the abdomen and visceral compartment toward the hips, thighs, glutes, and face — producing a more feminine silhouette. This is a slow, continuous process driven by estrogen receptor signalling in adipocytes. Maximum effect takes 2-5 years of consistent therapy.",
        reversibility = "Reversible / Variable",
        researchNote = "Endocrine Society timeline table: onset 3-6 months, maximum 2-5 years."
    ),
    TimelineEffect(
        title = "Decreased Muscle Mass & Strength",
        onset = "3-6 months",
        maximum = "1-2 years",
        description = "Loss of muscle mass and raw physical strength, particularly in the upper body, occurs as testosterone levels suppress and anabolic signalling decreases. This mirrors the physiological difference between average male and female body composition. Resistance training can maintain strength during transition.",
        reversibility = "Reversible",
        researchNote = "Endocrine Society: onset 3-6 months, maximum effect at 1-2 years."
    ),
    TimelineEffect(
        title = "Decreased Testicular Volume",
        onset = "3-6 months",
        maximum = "2-3 years",
        description = "As LH/FSH suppression reduces gonadal activity, testicular tissue atrophies. Volume reduction occurs gradually. This is considered likely permanent after prolonged therapy, and fertility via spermatogenesis may be significantly impaired. Pre-therapy sperm banking is strongly recommended for those desiring genetic offspring.",
        reversibility = "Likely Permanent",
        researchNote = "WPATH SOC8 & Endocrine Society: sperm banking advised before initiating feminizing HRT."
    ),
    TimelineEffect(
        title = "Body & Facial Hair Slowdown",
        onset = "6-12 months",
        maximum = "3-5 years",
        description = "Androgenic terminal hairs on the body slow their growth rate and gradually become finer and lighter. Body hair responds well to hormones alone. Facial hair, however, is deeply rooted and testosterone-primed — it slows significantly but rarely disappears without professional laser hair removal or electrolysis.",
        reversibility = "Irreversible (slowing)",
        researchNote = "Endocrine Society: body/facial hair changes take longest — up to 5 years for maximum effect."
    ),
    TimelineEffect(
        title = "Emotional Lability & Increased Empathy",
        onset = "1-3 months",
        maximum = "6-12 months",
        description = "Estradiol fundamentally re-engineers neurological emotional regulation. A dramatically lowered threshold for crying and a heightened capacity to read others' emotions are universal early effects. This is neurochemical in nature — estrogen alters lacrimal gland activity. While occasionally intense, patients overwhelmingly report this as affirming and experience a net reduction in depression and anxiety.",
        reversibility = "Reversible",
        researchNote = "Clinical & qualitative studies: neurodivergent individuals may experience more intense emotional shifts. Stanford 2022: early GAHT access correlates with significantly better long-term mental health outcomes."
    ),
    TimelineEffect(
        title = "Cyclical Symptoms (PMS-like)",
        onset = "2-6 months",
        maximum = "Ongoing",
        description = "A significant subset of transfeminine individuals on estrogen report monthly cyclical symptoms including breast tenderness, abdominal cramping, bloating, mood shifts, and heightened irritability — analogous to PMS/PMDD. These manifest when serum estradiol troughs fall below personal baseline, particularly with inconsistent dosing or injection cycle troughs. The IAPD acknowledges this as a validated somatic experience.",
        reversibility = "Ongoing — dose stabilisation reduces severity",
        researchNote = "IAPD & qualitative research: symptoms linked to estrogenic fluctuations, especially with injection cycle troughs or inconsistent oral dosing."
    ),
    TimelineEffect(
        title = "Sperm Production Decrease (Fertility)",
        onset = "Variable",
        maximum = "Variable",
        description = "Suppression of spermatogenesis begins early and may become permanent with prolonged therapy. Cessation of HRT may occasionally restore fertility, but this is highly unpredictable. Sperm banking is strongly advised before starting feminizing HRT for anyone desiring genetic offspring.",
        reversibility = "Possibly Permanent",
        researchNote = "WPATH SOC8: banking typically requires 2-4 weeks. Recovery of spermatogenesis after cessation is not guaranteed."
    )
)

val mascEffects = listOf(
    TimelineEffect(
        title = "Skin Oiliness & Acne Increase",
        onset = "1-6 months",
        maximum = "1-2 years",
        description = "Androgens stimulate the sebaceous glands, rapidly increasing sebum production. Acne — often cystic — frequently emerges on the face, back, and chest within the first months. This is one of the most universally experienced early changes. Targeted dermatological care (retinoids, benzoyl peroxide, or isotretinoin for severe cases) is often needed.",
        reversibility = "Reversible",
        researchNote = "Endocrine Society table: onset 1-6 months, maximum 1-2 years."
    ),
    TimelineEffect(
        title = "Cessation of Menses (Amenorrhea)",
        onset = "1-6 months",
        maximum = "1-2 years",
        description = "Menstrual cycles typically become lighter and irregular within the first months, then cease completely as testosterone suppresses the hypothalamic-pituitary-ovarian axis. Full amenorrhea is achieved in most individuals within 6 months — this is a key clinical milestone confirming adequate androgen exposure. Irregular bleeding beyond 6 months warrants clinical review.",
        reversibility = "Reversible",
        researchNote = "Endocrine Society: cessation within 6 months confirms axis suppression. Note: testosterone is NOT a reliable contraceptive."
    ),
    TimelineEffect(
        title = "Clitoral Enlargement (Bottom Growth)",
        onset = "1-6 months",
        maximum = "1-2 years",
        description = "Clitoral hypertrophy is typically one of the earliest and most affirming physical changes. The clitoris and penis share homologous embryonic origins, so testosterone powerfully stimulates androgen receptors in the erectile tissue. Initial changes are palpable within 1-3 months; typical growth is 1-5 cm. Early tactile hypersensitivity and discomfort from friction against the clitoral hood is common but temporary.",
        reversibility = "Irreversible",
        researchNote = "Clinical literature: plateau reached at 1-2 years. Qualitative studies consistently describe this as deeply affirming and a significant reduction in genital dysphoria."
    ),
    TimelineEffect(
        title = "Body Fat Redistribution",
        onset = "1-6 months",
        maximum = "2-5 years",
        description = "Subcutaneous fat migrates away from the gluteofemoral region (hips and thighs) toward the abdomen and visceral compartment — creating a more angular, masculine silhouette. Karolinska MRI research (6-year cohort) recorded a staggering 70% increase in visceral abdominal fat alongside a 21% increase in lean muscle. This visceral fat shift elevates metabolic risk markers and warrants active management.",
        reversibility = "Reversible / Variable",
        researchNote = "Karolinska MRI cohort: 70% visceral fat increase paired with metabolic profile changes (elevated LDL, reduced HDL)."
    ),
    TimelineEffect(
        title = "Facial & Body Hair Growth",
        onset = "3-6 months",
        maximum = "4-5 years",
        description = "New dark, coarse terminal hairs emerge on the upper lip, chin, cheeks, sideburns, chest, abdomen, arms, and legs. The rate and density of growth is highly genetically determined (compare to male relatives). This is a slow, multi-year process — full masculine facial hair density typically takes 4-5 years. Hair development is irreversible.",
        reversibility = "Irreversible",
        researchNote = "Endocrine Society table: onset 3-6 months, maximum 4-5 years."
    ),
    TimelineEffect(
        title = "Voice Deepening",
        onset = "6-12 months",
        maximum = "1-2 years",
        description = "Testosterone induces laryngeal cartilage hypertrophy and vocal fold thickening, causing a permanent, irreversible drop in fundamental speaking pitch. One documented cohort saw pitch fall from 183 Hz (female range) to 134 Hz (male range) within 37 weeks. A 'cracking' or 'turbulent' transition phase is normal. Contrary to myth, the singing voice shifts range (e.g., soprano → tenor/baritone) rather than being destroyed — speech pathology can assist.",
        reversibility = "Irreversible",
        researchNote = "PMC acoustic case study: 183 Hz → 134 Hz within 37 weeks. Speech-language pathologists specializing in TGD care provide targeted support."
    ),
    TimelineEffect(
        title = "Increased Muscle Mass & Strength",
        onset = "6-12 months",
        maximum = "2-5 years",
        description = "Anabolic androgenic signalling drives significant skeletal muscle hypertrophy and increased strength, particularly in the upper body. The Karolinska study recorded an average 21% increase in lean muscle volume over 6 years. Combining testosterone with progressive resistance training significantly amplifies these gains.",
        reversibility = "Reversible",
        researchNote = "Endocrine Society table: onset 6-12 months, maximum 2-5 years. Karolinska 6-yr MRI cohort: 21% average lean volume increase."
    ),
    TimelineEffect(
        title = "Scalp Hair Loss (Male Pattern)",
        onset = "> 12 months",
        maximum = "Variable (genetic)",
        description = "Testosterone is converted to DHT by 5-alpha reductase enzymes. DHT causes follicular miniaturization in genetically predisposed individuals, leading to male-pattern hair loss. Risk and pattern directly mirror that of male relatives. Finasteride or dutasteride (5-ARIs) can halt or slow this if hair preservation is desired.",
        reversibility = "Irreversible",
        researchNote = "Endocrine Society: onset > 12 months; extent is genetically variable. 5-ARIs are effective at halting progression without counteracting virilization."
    ),
    TimelineEffect(
        title = "Vulvovaginal Atrophy",
        onset = "1-6 months",
        maximum = "1-2 years",
        description = "High-dose testosterone suppresses estrogen, which is required to maintain vaginal mucosal integrity. Estrogen deprivation causes atrophic vaginitis: vaginal dryness, thinning mucosa, burning, dyspareunia (painful intercourse), and increased susceptibility to infections. Topical vaginal estrogen (Estrace, Imvexxy rings) is the gold standard treatment — clinical evidence confirms it acts locally only and will not counteract systemic virilization.",
        reversibility = "Reversible",
        researchNote = "UCSF guidelines & Mayo Clinic: topical vaginal estrogen is the gold-standard. Systemic absorption is negligible — it will not re-feminize or induce menstruation."
    ),
    TimelineEffect(
        title = "Emotional Changes & Crying Inhibition",
        onset = "1-6 months",
        maximum = "Ongoing",
        description = "Testosterone alters the neurochemistry of central emotional processing and lacrimal gland activity. A widely reported effect is profound difficulty or complete inability to cry ('T-cold'), even during intense grief or joy. This is physiological, not psychological, and is not indicative of reduced empathy. Many also report a reduction in generalised anxiety and improved emotional stability — though mood lability can worsen with large injection peak-trough swings.",
        reversibility = "Partially reversible",
        researchNote = "Clinical literature: T-cold is an endocrinological consequence of elevated T and suppressed E2. Mood lability from injection peaks/troughs improved by weekly vs bi-weekly dosing."
    )
)



fun adjustTimelineString(timeline: String, stretchRatio: Float): String {
    if (stretchRatio <= 1.0f) return timeline
    val numberRegex = Regex("\\b\\d+(?:\\.\\d+)?\\b")
    return numberRegex.replace(timeline) { matchResult ->
        val originalVal = matchResult.value.toDoubleOrNull() ?: return@replace matchResult.value
        val scaledVal = originalVal * stretchRatio
        if (scaledVal % 1.0 == 0.0) {
            scaledVal.toInt().toString()
        } else {
            "%.1f".format(Locale.US, scaledVal)
        }
    }
}
