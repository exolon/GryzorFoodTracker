package com.example.gryzorfoodtracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorScreen(navController: NavController, db: AppDatabase) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dao = db.mealDao()
    val today = LocalDate.now()

    val phasePreference by context.dataStore.data
        .map { it[PHASE_MODE_KEY] ?: "cut" }
        .collectAsState(initial = "cut")

    val allMetrics by dao.getAllMetrics().collectAsState(initial = emptyList())
    val allTags by dao.getAllTags().collectAsState(initial = emptyList())

    val last14Days = remember(today) { (13 downTo 0).map { today.minusDays(it.toLong()).toString() } }

    // --- 1. THE CALORIC VIX ---
    val vixScore = remember(last14Days, allMetrics) {
        val kcals = allMetrics.filter { last14Days.contains(it.date) }.mapNotNull { it.totalKcal.toDoubleOrNull() }
        if (kcals.size < 2) 0 else {
            val mean = kcals.average()
            val variance = kcals.map { (it - mean).pow(2) }.average()
            sqrt(variance).toInt()
        }
    }

    // --- 2. MARGINAL FUEL ROI ---
    val fuelEfficiency = remember(last14Days, allMetrics, allTags) {
        var totalSurplusDays = 0
        var strategicSurplusDays = 0
        last14Days.forEach { date ->
            val metric = allMetrics.find { it.date == date }
            val def = metric?.deficit?.toDoubleOrNull() ?: 0.0
            if (def < 0) {
                totalSurplusDays++
                val tagStr = allTags.find { it.date == date }?.tags ?: ""
                if (tagStr.contains("Grind", ignoreCase = true) || tagStr.contains("Upper Body Bias", ignoreCase = true)) {
                    strategicSurplusDays++
                }
            }
        }
        if (totalSurplusDays == 0) 100 else ((strategicSurplusDays.toFloat() / totalSurplusDays) * 100).toInt()
    }

    // --- 3. PREDICTIVE DEGRADATION (BURNOUT METER) ---
    val burnoutRisk = remember(last14Days, allMetrics, vixScore, phasePreference) {
        if (phasePreference == "bulk") 0
        else {
            var streak = 0
            var recentDeficitSum = 0.0
            val daysReversed = last14Days.reversed()
            for (date in daysReversed) {
                val metric = allMetrics.find { it.date == date }
                val def = metric?.deficit?.toDoubleOrNull() ?: 0.0
                if (def > 0) {
                    streak++
                    recentDeficitSum += def
                } else if (def < 0) {
                    break
                }
            }
            val avgStreakDef = if (streak > 0) recentDeficitSum / streak else 0.0
            var risk = streak * 10
            if (avgStreakDef > 600) risk += 15
            if (vixScore > 300) risk += 15
            risk.coerceIn(0, 100)
        }
    }

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)) }

    // Tooltip States
    var showBurnoutTooltip by remember { mutableStateOf(false) }
    var showVixTooltip by remember { mutableStateOf(false) }
    var showRoiTooltip by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Behavioral Engine") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            fun elasticMod(index: Int) = Modifier.offset(y = (40.dp * (1f - entrance.value) * (index + 1))).alpha(entrance.value)

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // --- BURNOUT METER ---
            item {
                Column(modifier = elasticMod(0).fillMaxWidth().padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Predictive Degradation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(20.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showBurnoutTooltip = !showBurnoutTooltip }
                        ) { Icon(imageVector = Icons.Filled.QuestionMark, contentDescription = "Info", modifier = Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text("Real-time psychological bandwidth & failure risk.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                    AnimatedVisibility(visible = showBurnoutTooltip) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)) {
                            Text("Combines consecutive deficit days, deficit severity, and Caloric VIX to calculate the statistical probability of a diet failure. Reach 80% and a Maintenance break is highly recommended.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)) {
                            if (phasePreference == "bulk") {
                                Text("Burnout tracking is currently disabled while in Bulk phase.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            } else {
                                val meterColor = if (burnoutRisk >= 80) MaterialTheme.colorScheme.error else if (burnoutRisk >= 50) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = meterColor, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("$burnoutRisk% Burnout Risk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = meterColor)
                                }
                                Spacer(Modifier.height(16.dp))

                                LinearProgressIndicator(
                                    progress = { burnoutRisk / 100f },
                                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                    color = meterColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))

                                val riskText = if (burnoutRisk >= 80) "Critical Fatigue. Willpower reserves depleted. A tactical Maintenance day is mathematically required to prevent rubber-band bingeing." else if (burnoutRisk >= 50) "Moderate Fatigue. Deficit streak is taxing the system. Keep an eye on daily VIX volatility." else "System Stable. High cognitive bandwidth available. Clear to continue Grind protocols."

                                Surface(color = meterColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                    Text(riskText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // --- VIX & ROI SECTION ---
            item {
                Column(modifier = elasticMod(1).fillMaxWidth().padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 40.dp)) {
                    Text("System Economics", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // VIX Card
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    val vixColor = if (vixScore > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    Icon(Icons.Filled.SsidChart, contentDescription = null, tint = vixColor, modifier = Modifier.size(24.dp))
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(20.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showVixTooltip = !showVixTooltip }) { Icon(imageVector = Icons.Filled.QuestionMark, contentDescription = "Info", modifier = Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }

                                AnimatedVisibility(visible = showVixTooltip) {
                                    Text("Measures the standard deviation of your daily caloric intake. High volatility (>300) is often a leading indicator of erratic habits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp))
                                }

                                Spacer(Modifier.height(12.dp))
                                val vixColor = if (vixScore > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                Text("Caloric VIX", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text("$vixScore SD", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = vixColor)
                                Spacer(Modifier.height(4.dp))
                                Text(if (vixScore > 300) "High Volatility" else "Stable Variance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // ROI Card
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    val roiColor = if (fuelEfficiency >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    Icon(Icons.Filled.PriceCheck, contentDescription = null, tint = roiColor, modifier = Modifier.size(24.dp))
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(20.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showRoiTooltip = !showRoiTooltip }) { Icon(imageVector = Icons.Filled.QuestionMark, contentDescription = "Info", modifier = Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }

                                AnimatedVisibility(visible = showRoiTooltip) {
                                    Text("The percentage of your 14-day caloric surpluses that successfully aligned with 'Grind' or 'Upper Body Bias' days for muscle synthesis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp))
                                }

                                Spacer(Modifier.height(12.dp))
                                val roiColor = if (fuelEfficiency >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Text("Fuel ROI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text("$fuelEfficiency%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = roiColor)
                                Spacer(Modifier.height(4.dp))
                                Text(if (fuelEfficiency >= 80) "Capital Efficient" else "High Leakage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}