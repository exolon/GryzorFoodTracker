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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.material.icons.filled.TrendingFlat
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
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

    val targetWeightStr by context.dataStore.data
        .map { it[TARGET_WEIGHT_KEY] ?: "" }
        .collectAsState(initial = "")

    val allMetrics by dao.getAllMetrics().collectAsState(initial = emptyList())
    val allTags by dao.getAllTags().collectAsState(initial = emptyList())
    val allMeasurements by dao.getAllMeasurements().collectAsState(initial = emptyList())

    val last14Days = remember(today) { (13 downTo 0).map { today.minusDays(it.toLong()).toString() } }
    val daysReversed = remember(last14Days) { last14Days.reversed() }

    val vixScore = remember(last14Days, allMetrics) {
        val kcals = allMetrics.filter { last14Days.contains(it.date) }.mapNotNull { it.totalKcal.toDoubleOrNull() }
        if (kcals.size < 2) 0 else {
            val mean = kcals.average()
            val variance = kcals.map { (it - mean).pow(2) }.average()
            sqrt(variance).toInt()
        }
    }

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

    val burnoutRisk = remember(daysReversed, allMetrics, vixScore, phasePreference) {
        if (phasePreference == "bulk") 0
        else {
            var streak = 0
            var recentDeficitSum = 0.0
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

    val momentumData = remember(daysReversed, allMetrics) {
        val recent3 = daysReversed.take(3).mapNotNull { date ->
            allMetrics.find { it.date == date }?.deficit?.toDoubleOrNull()
        }
        val avg3 = if (recent3.isNotEmpty()) recent3.average() else 0.0

        val all14 = daysReversed.mapNotNull { date ->
            allMetrics.find { it.date == date }?.deficit?.toDoubleOrNull()
        }
        val avg14 = if (all14.isNotEmpty()) all14.average() else 0.0

        Pair(avg3.toInt(), avg14.toInt())
    }

    val paretoLeakage = remember(last14Days, allMetrics, allTags) {
        val surplusDays = last14Days.filter { date ->
            val def = allMetrics.find { it.date == date }?.deficit?.toDoubleOrNull() ?: 0.0
            def < 0
        }
        if (surplusDays.isEmpty()) Pair("None", 0) else {
            val tagCounts = mutableMapOf<String, Int>()
            surplusDays.forEach { date ->
                val tags = allTags.find { it.date == date }?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (tags.isEmpty()) {
                    tagCounts["Untagged"] = tagCounts.getOrDefault("Untagged", 0) + 1
                } else {
                    tags.forEach { tag -> tagCounts[tag] = tagCounts.getOrDefault(tag, 0) + 1 }
                }
            }
            val worstTag = tagCounts.maxByOrNull { it.value }
            if (worstTag != null) {
                Pair(worstTag.key, ((worstTag.value.toFloat() / surplusDays.size) * 100).toInt())
            } else {
                Pair("None", 0)
            }
        }
    }

    val frictionData = remember(last14Days, allMetrics, allTags, phasePreference) {
        var highFrictionDays = 0
        var highFrictionWins = 0
        last14Days.forEach { date ->
            val tags = allTags.find { it.date == date }?.tags ?: ""
            if (tags.contains("Friction:", ignoreCase = true)) {
                val level = tags.split(",").find { it.trim().startsWith("Friction:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                if (level >= 4) {
                    highFrictionDays++
                    val def = allMetrics.find { it.date == date }?.deficit?.toDoubleOrNull() ?: 0.0
                    val isWin = if (phasePreference == "bulk") def < 0 else def > 0
                    if (isWin) highFrictionWins++
                }
            }
        }
        if (highFrictionDays == 0) -1 else ((highFrictionWins.toFloat() / highFrictionDays) * 100).toInt()
    }

    // --- V4.2 RECOVERY DEBT RATIO ---
    val recoveryDebt = remember(last14Days, allTags) {
        var grind = 0
        var rest = 0
        last14Days.forEach { d ->
            val t = allTags.find { it.date == d }?.tags ?: ""
            if (t.contains("Grind", ignoreCase = true)) grind++
            if (t.contains("Rest", ignoreCase = true)) rest++
        }
        val ratio = if (rest == 0) grind.toFloat() else grind.toFloat() / rest
        Triple(grind, rest, ratio)
    }

    // --- V4.2 VELOCITY BURN-DOWN FORECAST ---
    val burnDownForecast = remember(last14Days, allMeasurements, targetWeightStr, phasePreference) {
        val target = targetWeightStr.toFloatOrNull()
        val sortedMeasures = last14Days.mapNotNull { d ->
            allMeasurements.find { it.date == d }?.weight?.toFloatOrNull()?.let { Pair(LocalDate.parse(d), it) }
        }.sortedBy { it.first }

        if (target == null || sortedMeasures.size < 2) {
            "Insufficient Data"
        } else {
            val first = sortedMeasures.first()
            val last = sortedMeasures.last()
            val daysBetween = ChronoUnit.DAYS.between(first.first, last.first)

            if (daysBetween == 0L) {
                "Calculating..."
            } else {
                val dailyVelocity = (last.second - first.second) / daysBetween.toFloat()
                val weightToGo = target - last.second

                if (phasePreference == "cut") {
                    if (dailyVelocity >= 0) {
                        "Velocity Stalled"
                    } else if (weightToGo >= 0) {
                        "Target Achieved"
                    } else {
                        val daysLeft = (weightToGo / dailyVelocity).toLong()
                        LocalDate.now().plusDays(daysLeft).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    }
                } else {
                    if (dailyVelocity <= 0) {
                        "Velocity Stalled"
                    } else if (weightToGo <= 0) {
                        "Target Achieved"
                    } else {
                        val daysLeft = (weightToGo / dailyVelocity).toLong()
                        LocalDate.now().plusDays(daysLeft).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    }
                }
            }
        }
    }

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)
        )
    }

    var showBurnoutTooltip by remember { mutableStateOf(false) }
    var showVixTooltip by remember { mutableStateOf(false) }
    var showRoiTooltip by remember { mutableStateOf(false) }
    var showMomentumTooltip by remember { mutableStateOf(false) }
    var showParetoTooltip by remember { mutableStateOf(false) }
    var showEgoTooltip by remember { mutableStateOf(false) }
    var showRecoveryTooltip by remember { mutableStateOf(false) }
    var showVelocityTooltip by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Behavioral Engine") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
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
            fun elasticMod(index: Int) = Modifier
                .offset(y = (40.dp * (1f - entrance.value) * (index + 1)))
                .alpha(entrance.value)

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // --- SECTION 1: BURNOUT METER ---
            item {
                Column(
                    modifier = elasticMod(0)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Predictive Degradation",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showBurnoutTooltip = !showBurnoutTooltip
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QuestionMark,
                                contentDescription = "Info",
                                modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Real-time psychological bandwidth & failure risk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    AnimatedVisibility(visible = showBurnoutTooltip) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = "Calculation: [Streak Days x 10] + [Deficit Intensity Penalty] + [Metabolic Volatility Penalty]. It predicts when the 'rubber-band effect' will trigger a binge based on cumulative biological stress.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            if (phasePreference == "bulk") {
                                Text(
                                    text = "Burnout tracking is currently disabled while in Bulk phase.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                val meterColor = if (burnoutRisk >= 80) MaterialTheme.colorScheme.error else if (burnoutRisk >= 50) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.BatteryAlert,
                                        contentDescription = null,
                                        tint = meterColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "$burnoutRisk% Burnout Risk",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = meterColor
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                LinearProgressIndicator(
                                    progress = { burnoutRisk / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    color = meterColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(Modifier.height(16.dp))

                                val riskText = if (burnoutRisk >= 80) "Critical Fatigue. Willpower reserves depleted. A tactical Maintenance day is mathematically required." else if (burnoutRisk >= 50) "Moderate Fatigue. Deficit streak is taxing the system." else "System Stable. High cognitive bandwidth available."
                                Surface(
                                    color = meterColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = riskText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: SYSTEM ECONOMICS ---
            item {
                Column(
                    modifier = elasticMod(1)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = "System Economics",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val vixColor = if (vixScore > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    Icon(
                                        imageVector = Icons.Filled.SsidChart,
                                        contentDescription = null,
                                        tint = vixColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showVixTooltip = !showVixTooltip
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QuestionMark,
                                            contentDescription = "Info",
                                            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showVixTooltip) {
                                    Text(
                                        text = "Calculation: Standard Deviation (σ) of daily calories over 14 days.\n1. Mean (μ) = Avg daily calories.\n2. Variance = Avg of squared differences from the Mean.\n3. VIX = Square root of Variance.\nHigh volatility (>300) indicates erratic eating patterns.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                val vixColor = if (vixScore > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                Text(
                                    text = "Caloric VIX",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "$vixScore SD",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = vixColor
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (vixScore > 300) "High Volatility" else "Stable Variance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val roiColor = if (fuelEfficiency >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    Icon(
                                        imageVector = Icons.Filled.PriceCheck,
                                        contentDescription = null,
                                        tint = roiColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showRoiTooltip = !showRoiTooltip
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QuestionMark,
                                            contentDescription = "Info",
                                            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showRoiTooltip) {
                                    Text(
                                        text = "Calculation: (Surpluses on Grind Days / Total Surplus Days) * 100. Measures if 'leakage' is efficiently building muscle or just storing fat.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                val roiColor = if (fuelEfficiency >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Text(
                                    text = "Fuel ROI",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "$fuelEfficiency%",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = roiColor
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (fuelEfficiency >= 80) "Capital Efficient" else "High Leakage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 3: ATTRIBUTION & TRENDS ---
            item {
                Column(
                    modifier = elasticMod(2)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = "Attribution & Trends",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isGood = if (phasePreference == "bulk") momentumData.first <= momentumData.second else momentumData.first >= momentumData.second
                                    val icon = if (momentumData.first > momentumData.second) Icons.Filled.ArrowUpward else if (momentumData.first < momentumData.second) Icons.Filled.ArrowDownward else Icons.Filled.TrendingFlat
                                    val iconColor = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showMomentumTooltip = !showMomentumTooltip
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QuestionMark,
                                            contentDescription = "Info",
                                            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showMomentumTooltip) {
                                    Text(
                                        text = "Calculation: Compares your 3-Day Trailing Avg vs 14-Day Trailing Avg. Identifies if your current trend is accelerating or decaying.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = "Momentum",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                val isGood = if (phasePreference == "bulk") momentumData.first <= momentumData.second else momentumData.first >= momentumData.second
                                Text(
                                    text = "${momentumData.first}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "vs ${momentumData.second} (14D)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showParetoTooltip = !showParetoTooltip
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QuestionMark,
                                            contentDescription = "Info",
                                            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showParetoTooltip) {
                                    Text(
                                        text = "Calculation: Identifies the specific Context Tag most mathematically correlated with a failed deficit (surplus) over the last 14 days.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = "Pareto Leakage",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = paretoLeakage.first,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${paretoLeakage.second}% of surplus days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 4: COGNITIVE LOAD ---
            item {
                Column(
                    modifier = elasticMod(3)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = "Cognitive Load",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val egoColor = if (frictionData == -1) MaterialTheme.colorScheme.outline else if (frictionData >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        tint = egoColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Ego Depletion Matrix",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = egoColor
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showEgoTooltip = !showEgoTooltip
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.QuestionMark,
                                        contentDescription = "Info",
                                        modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showEgoTooltip) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 0.dp)
                                ) {
                                    Text(
                                        text = "Calculation: Measures your success rate specifically on days tagged 'Load 4' or 'Load 5'. Proves statistically if your adherence drops when your cognitive bandwidth is compromised by work/life demands.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            if (frictionData == -1) {
                                Text(
                                    text = "Matrix Inactive. Use the 'Load' picker next to the Date on the Home screen to tag high-stress days.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                val egoColor = if (frictionData >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Text(
                                    text = "$frictionData% Adherence under High Friction",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = egoColor
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (frictionData >= 70) "Strong cognitive resilience. You are executing the plan despite systemic stress." else "Ego Depletion confirmed. High friction consistently breaks your adherence. Pre-plan Maintenance calories on high-stress days to prevent failure.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 5: PHYSIOLOGY & VELOCITY (V4.2) ---
            item {
                Column(
                    modifier = elasticMod(4)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 40.dp)
                ) {
                    Text(
                        text = "Physiology & Velocity",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Recovery Debt Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val ratio = recoveryDebt.third
                                    val isDeloading = recoveryDebt.first == 0
                                    val debtColor = if (isDeloading) MaterialTheme.colorScheme.primary else if (ratio >= 4.0) MaterialTheme.colorScheme.error else if (ratio >= 2.0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary

                                    Icon(
                                        imageVector = Icons.Filled.Hotel,
                                        contentDescription = null,
                                        tint = debtColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showRecoveryTooltip = !showRecoveryTooltip
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QuestionMark,
                                            contentDescription = "Info",
                                            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showRecoveryTooltip) {
                                    Text(
                                        text = "Calculation: Ratio of 'Grind' to 'Rest' tags over 14 days. Exceeding a 4.0 ratio indicates severe CNS fatigue and triggers a mandatory deload warning.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = "Recovery Debt",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                val ratio = recoveryDebt.third
                                val isDeloading = recoveryDebt.first == 0
                                val debtColor = if (isDeloading) MaterialTheme.colorScheme.primary else if (ratio >= 4.0) MaterialTheme.colorScheme.error else if (ratio >= 2.0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                                val statusText = if (isDeloading) "Deloading" else if (ratio >= 4.0) "Critical Debt" else if (ratio >= 2.0) "High Strain" else "Sustainable"

                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = debtColor
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${recoveryDebt.first} Grind : ${recoveryDebt.second} Rest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Velocity Burn-Down Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isGood = !burnDownForecast.contains("Stalled") && !burnDownForecast.contains("Insufficient")
                                    Icon(
                                        imageVector = Icons.Filled.Event,
                                        contentDescription = null,
                                        tint = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showVelocityTooltip = !showVelocityTooltip
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QuestionMark,
                                            contentDescription = "Info",
                                            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 3.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showVelocityTooltip) {
                                    Text(
                                        text = "Calculation: Trailing 14-day weight velocity projected linearly against your Target Weight (set in Options). Phase-aware (Cut/Bulk).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = "Target Horizon",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                val isGood = !burnDownForecast.contains("Stalled") && !burnDownForecast.contains("Insufficient")
                                Text(
                                    text = burnDownForecast,
                                    style = if (burnDownForecast.length > 15) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (targetWeightStr.isBlank()) "Set Target in Options" else "Goal: ${targetWeightStr}kg",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}