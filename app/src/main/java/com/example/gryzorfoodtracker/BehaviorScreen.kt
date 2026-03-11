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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.material.icons.filled.Star
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorScreen(
    navController: NavController,
    db: AppDatabase
) {
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

    val customTags by context.dataStore.data
        .map { it[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS }
        .collectAsState(initial = DEFAULT_TAGS)

    val allMetrics by dao.getAllMetrics().collectAsState(initial = emptyList())
    val allTags by dao.getAllTags().collectAsState(initial = emptyList())
    val allMeasurements by dao.getAllMeasurements().collectAsState(initial = emptyList())

    val last14Days = remember(today) {
        (13 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }
    val daysReversed = remember(last14Days) {
        last14Days.reversed()
    }

    val vixScore = remember(last14Days, allMetrics) {
        val intakes = allMetrics.filter {
            last14Days.contains(it.date)
        }.mapNotNull {
            it.totalKcal.toDoubleOrNull()
        }

        if (intakes.size < 2) {
            0
        } else {
            val mean = intakes.average()
            val variance = intakes.map { (it - mean).pow(2) }.average()
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

        if (totalSurplusDays == 0) {
            100
        } else {
            ((strategicSurplusDays.toFloat() / totalSurplusDays) * 100).toInt()
        }
    }

    // --- V4.7 WEEKLY P&L LOGIC ---
    val weeklyPnL = remember(today, allMetrics, allMeasurements, phasePreference) {
        val last7Days = (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
        val defSum = allMetrics.filter { last7Days.contains(it.date) }.sumOf { it.deficit.toDoubleOrNull() ?: 0.0 }

        // 7700 kcal per kg of fat
        val expectedDelta = defSum / 7700.0

        val measures = allMeasurements.filter { last7Days.contains(it.date) }
            .mapNotNull { m -> m.weight.toDoubleOrNull()?.let { LocalDate.parse(m.date) to it } }
            .sortedBy { it.first }

        val actualDelta = if (measures.size >= 2) {
            val firstW = measures.first().second
            val lastW = measures.last().second
            // Positive value means weight was lost, Negative means gained
            firstW - lastW
        } else {
            null
        }

        Pair(expectedDelta, actualDelta)
    }

    val burnoutRisk = remember(daysReversed, allMetrics, allTags, vixScore, phasePreference) {
        if (phasePreference == "bulk") {
            0
        } else {
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
            var risk = streak * 5

            if (avgStreakDef > 600) risk += 15
            if (vixScore > 300) risk += 15

            var recentSleep = 0
            for (date in daysReversed) {
                val tags = allTags.find { it.date == date }?.tags ?: ""
                val sleep = tags.split(",").find { it.trim().startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                if (sleep > 0) {
                    recentSleep = sleep
                    break
                }
            }
            if (recentSleep in 1..2) {
                risk += 15
            }

            risk.coerceIn(0, 100)
        }
    }

    val momentumData = remember(daysReversed, allMetrics) {
        val recent3 = daysReversed.take(3).map { date ->
            allMetrics.find { it.date == date }?.deficit?.toDoubleOrNull() ?: 0.0
        }

        val avg3 = if (recent3.size == 3) {
            (recent3[0] * 0.50) + (recent3[1] * 0.33) + (recent3[2] * 0.17)
        } else if (recent3.isNotEmpty()) {
            recent3.average()
        } else {
            0.0
        }

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

        if (surplusDays.isEmpty()) {
            Pair("None", 0)
        } else {
            val tagCounts = mutableMapOf<String, Int>()
            surplusDays.forEach { date ->
                val tags = allTags.find { it.date == date }?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (tags.isEmpty()) {
                    tagCounts["Untagged"] = tagCounts.getOrDefault("Untagged", 0) + 1
                } else {
                    tags.forEach { tag ->
                        tagCounts[tag] = tagCounts.getOrDefault(tag, 0) + 1
                    }
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

    // --- V4.7 SUCCESS BLUEPRINT ---
    val successBlueprint = remember(today, allMetrics, allTags, phasePreference, customTags) {
        val last30Days = (29 downTo 0).map { today.minusDays(it.toLong()).toString() }
        val winDays = last30Days.filter { d ->
            val def = allMetrics.find { it.date == d }?.deficit?.toDoubleOrNull() ?: 0.0
            if (phasePreference == "bulk") def < 0 else def > 0
        }

        if (winDays.isEmpty()) {
            "Insufficient Data"
        } else {
            var sleepSum = 0
            var sleepCount = 0
            var loadSum = 0
            var loadCount = 0
            val tagFreq = mutableMapOf<String, Int>()

            winDays.forEach { d ->
                val tStr = allTags.find { it.date == d }?.tags ?: ""
                val tList = tStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

                val sleep = tList.find { it.startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toIntOrNull()
                if (sleep != null && sleep > 0) {
                    sleepSum += sleep
                    sleepCount++
                }

                val load = tList.find { it.startsWith("Friction:") }?.substringAfter(":")?.trim()?.toIntOrNull()
                if (load != null && load > 0) {
                    loadSum += load
                    loadCount++
                }

                tList.filter { !it.startsWith("Friction:") && !it.startsWith("Sleep:") && customTags.contains(it) }.forEach {
                    tagFreq[it] = tagFreq.getOrDefault(it, 0) + 1
                }
            }

            val avgSleep = if (sleepCount > 0) (sleepSum.toFloat() / sleepCount).roundToInt() else 0
            val avgLoad = if (loadCount > 0) (loadSum.toFloat() / loadCount).roundToInt() else 0
            val topTag = tagFreq.maxByOrNull { it.value }?.key

            val parts = mutableListOf<String>()
            if (avgLoad > 0) parts.add("Load $avgLoad")
            if (avgSleep > 0) parts.add("Sleep $avgSleep")
            if (topTag != null) parts.add("[$topTag]")

            if (parts.isEmpty()) "Consistent Baseline" else parts.joinToString(" + ")
        }
    }

    val willpowerTax = remember(last14Days, allMetrics, allTags, phasePreference) {
        var poorSleepDays = 0
        var poorSleepWins = 0
        var goodSleepDays = 0
        var goodSleepWins = 0

        last14Days.forEach { date ->
            val tags = allTags.find { it.date == date }?.tags ?: ""
            val sleep = tags.split(",").find { it.trim().startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

            if (sleep > 0) {
                val def = allMetrics.find { it.date == date }?.deficit?.toDoubleOrNull() ?: 0.0
                val isWin = if (phasePreference == "bulk") def < 0 else def > 0

                if (sleep in 1..2) {
                    poorSleepDays++
                    if (isWin) poorSleepWins++
                } else if (sleep in 4..5) {
                    goodSleepDays++
                    if (isWin) goodSleepWins++
                }
            }
        }

        val poorRate = if (poorSleepDays == 0) -1 else ((poorSleepWins.toFloat() / poorSleepDays) * 100).toInt()
        val goodRate = if (goodSleepDays == 0) -1 else ((goodSleepWins.toFloat() / goodSleepDays) * 100).toInt()

        Pair(goodRate, poorRate)
    }

    val recoveryDebt = remember(last14Days, allTags) {
        var grind = 0.0
        var restAndRecovery = 0

        last14Days.forEach { d ->
            val t = allTags.find { it.date == d }?.tags ?: ""
            val sleep = t.split(",").find { it.trim().startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

            if (t.contains("Grind", ignoreCase = true) || t.contains("Upper Body Bias", ignoreCase = true)) {
                if (sleep in 1..2) {
                    grind += 1.5
                } else {
                    grind += 1.0
                }
            }
            if (t.contains("Rest", ignoreCase = true) || t.contains("Recovery", ignoreCase = true)) {
                restAndRecovery++
            }
        }

        val ratio = if (restAndRecovery == 0) grind.toFloat() else grind.toFloat() / restAndRecovery
        Triple(grind, restAndRecovery, ratio)
    }

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
    var showWillpowerTooltip by remember { mutableStateOf(false) }
    var showRecoveryTooltip by remember { mutableStateOf(false) }
    var showVelocityTooltip by remember { mutableStateOf(false) }
    var showPnlTooltip by remember { mutableStateOf(false) }
    var showBlueprintTooltip by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Behavioral Engine")
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.popBackStack()
                        }
                    ) {
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

            item {
                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }

            // --- SECTION 1: BURNOUT METER ---
            item {
                Column(
                    modifier = elasticMod(0)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Predictive Degradation",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                text = "Calculation: [Streak Days x 5] + [Deficit Intensity] + [Metabolic Volatility] + [Sleep Penalty]. Caps at 100%. 50%+ indicates moderate fatigue, 80%+ indicates critical cognitive depletion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BatteryAlert,
                                        contentDescription = null,
                                        tint = meterColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(
                                        modifier = Modifier.width(12.dp)
                                    )
                                    Text(
                                        text = "$burnoutRisk% Burnout Risk",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = meterColor
                                    )
                                }

                                Spacer(
                                    modifier = Modifier.height(16.dp)
                                )

                                LinearProgressIndicator(
                                    progress = { burnoutRisk / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    color = meterColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(
                                    modifier = Modifier.height(16.dp)
                                )

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
                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                        text = "Calculation: Standard Deviation (σ) of daily intake over 14 days. High volatility (>300) indicates erratic eating patterns and metabolic stress.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                val vixColor = if (vixScore > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                Text(
                                    text = "Intake VIX",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "$vixScore SD",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = vixColor
                                )
                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )
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
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

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
                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )
                                Text(
                                    text = if (fuelEfficiency >= 80) "Capital Efficient" else "High Leakage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    // --- V4.7 WEEKLY P&L CARD ---
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Calculate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(
                                        modifier = Modifier.width(12.dp)
                                    )
                                    Text(
                                        text = "Weekly P&L",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showPnlTooltip = !showPnlTooltip
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

                            AnimatedVisibility(visible = showPnlTooltip) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 0.dp)
                                ) {
                                    Text(
                                        text = "Calculation: Sums your net caloric deficit over the strict 7-day trailing window and divides by 7,700 kcal to calculate theoretical fat delta. Compares this mathematically against your actual scale change to validate your TDEE assumption.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                    )
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Theoretical Delta",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    val expectedStr = String.format("%.2f kg", weeklyPnL.first)
                                    val prefixE = if (weeklyPnL.first > 0) "-" else if (weeklyPnL.first < 0) "+" else ""

                                    Text(
                                        text = "$prefixE${abs(weeklyPnL.first).let { String.format("%.2f", it) }} kg",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(
                                    modifier = Modifier.width(16.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Actual Scale Delta",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    if (weeklyPnL.second == null) {
                                        Text(
                                            text = "--",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    } else {
                                        val actStr = String.format("%.2f kg", weeklyPnL.second!!)
                                        val prefixA = if (weeklyPnL.second!! > 0) "-" else if (weeklyPnL.second!! < 0) "+" else ""

                                        val varianceColor = if (abs((weeklyPnL.first) - (weeklyPnL.second!!)) <= 0.5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

                                        Text(
                                            text = "$prefixA${abs(weeklyPnL.second!!).let { String.format("%.2f", it) }} kg",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = varianceColor
                                        )
                                    }
                                }
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
                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                        text = "Calculation: Compares your 3-Day Weighted Moving Average (50% / 33% / 17%) vs 14-Day Trailing Avg. Identifies if your current trend is accelerating or decaying without overreacting to a single off-day.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

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
                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )
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
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

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
                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )
                                Text(
                                    text = "${paretoLeakage.second}% of surplus days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    // --- V4.7 SUCCESS BLUEPRINT CARD ---
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(
                                        modifier = Modifier.width(12.dp)
                                    )
                                    Text(
                                        text = "Success Blueprint",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showBlueprintTooltip = !showBlueprintTooltip
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

                            AnimatedVisibility(visible = showBlueprintTooltip) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 0.dp)
                                ) {
                                    Text(
                                        text = "Calculation: Scans your successful adherence days over the last 30 days and extracts the average Friction (Load), average Sleep Quality, and the most common Custom Tag present during successful execution.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                    )
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(16.dp)
                            )

                            Text(
                                text = "Optimal Conditions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Text(
                                text = successBlueprint,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // --- SECTION 4: THE WILLPOWER TAX ---
            item {
                Column(
                    modifier = elasticMod(3)
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = "The Willpower Tax",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                    val hasData = willpowerTax.first != -1 || willpowerTax.second != -1
                                    val iconColor = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(
                                        modifier = Modifier.width(12.dp)
                                    )
                                    Text(
                                        text = "Sleep vs Adherence",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = iconColor
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showWillpowerTooltip = !showWillpowerTooltip
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

                            AnimatedVisibility(visible = showWillpowerTooltip) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 0.dp)
                                ) {
                                    Text(
                                        text = "Calculation: Compares your deficit success rate on days logged with Good Sleep (Scores 4-5) versus Poor Sleep (Scores 1-2). Proves mathematically how compromised sleep degrades your cognitive discipline.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                    )
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(16.dp)
                            )

                            if (willpowerTax.first == -1 && willpowerTax.second == -1) {
                                Text(
                                    text = "Matrix Inactive. Log your Sleep Quality in the Morning Intent dashboard to generate this correlation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Good Sleep",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        val goodText = if (willpowerTax.first == -1) "--" else "${willpowerTax.first}%"
                                        Text(
                                            text = goodText,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Poor Sleep",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        val poorText = if (willpowerTax.second == -1) "--" else "${willpowerTax.second}%"
                                        val poorColor = if (willpowerTax.second != -1 && willpowerTax.second < 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                        Text(
                                            text = poorText,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = poorColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 5: PHYSIOLOGY & VELOCITY ---
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
                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Recovery Debt Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val ratio = recoveryDebt.third
                                    val isDeloading = recoveryDebt.first == 0.0
                                    val debtColor = if (isDeloading) MaterialTheme.colorScheme.primary else if (ratio >= 4.0) MaterialTheme.colorScheme.error else if (ratio >= 3.0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary

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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                        text = "Calculation: Ratio of 'Grind' to ('Rest' + 'Recovery') tags over 14 days. Grind days logged on Poor Sleep (1-2) are taxed at 1.5x. < 3.0 = Sustainable, 3.0+ = High Strain, 4.0+ = Critical Debt.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                                    )
                                }

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                Text(
                                    text = "Recovery Debt",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                val ratio = recoveryDebt.third
                                val isDeloading = recoveryDebt.first == 0.0
                                val debtColor = if (isDeloading) MaterialTheme.colorScheme.primary else if (ratio >= 4.0) MaterialTheme.colorScheme.error else if (ratio >= 3.0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                                val statusText = if (isDeloading) "Deloading" else if (ratio >= 4.0) "Critical Debt" else if (ratio >= 3.0) "High Strain" else "Sustainable"

                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = debtColor
                                )
                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )

                                val formatGrind = if (recoveryDebt.first % 1 == 0.0) recoveryDebt.first.toInt().toString() else recoveryDebt.first.toString()

                                Text(
                                    text = "$formatGrind Grind : ${recoveryDebt.second} Rest",
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
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            ) {
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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

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
                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )
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