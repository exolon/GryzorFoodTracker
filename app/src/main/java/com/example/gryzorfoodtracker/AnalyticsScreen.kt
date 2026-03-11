package com.example.gryzorfoodtracker

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

data class TagStat(
    val tag: String,
    val totalDays: Int,
    val winRate: Int,
    val avgDeficit: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    db: AppDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = db.mealDao()
    val today = LocalDate.now()
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    val phasePreference by context.dataStore.data
        .map { it[PHASE_MODE_KEY] ?: "cut" }
        .collectAsState(initial = "cut")

    val customTags by context.dataStore.data
        .map { it[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS }
        .collectAsState(initial = DEFAULT_TAGS)

    val (gyroPitch, gyroRoll) = rememberGyroscopeTilt()

    val allMetrics by dao.getAllMetrics().collectAsState(initial = emptyList())
    val allTags by dao.getAllTags().collectAsState(initial = emptyList())
    val allMeasurements by dao.getAllMeasurements().collectAsState(initial = emptyList())

    val currentWeekDates = remember(today) {
        (0..6).map { today.minusDays(it.toLong()).toString() }
    }
    val prevWeekDates = remember(today) {
        (7..13).map { today.minusDays(it.toLong()).toString() }
    }
    val last14Days = remember(today) {
        (13 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }
    val last31Days = remember(today) {
        (30 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }

    val currentKcal = allMetrics.filter {
        currentWeekDates.contains(it.date)
    }.mapNotNull {
        it.totalKcal.toDoubleOrNull()
    }

    val prevKcal = allMetrics.filter {
        prevWeekDates.contains(it.date)
    }.mapNotNull {
        it.totalKcal.toDoubleOrNull()
    }

    val currentDef = allMetrics.filter {
        currentWeekDates.contains(it.date)
    }.mapNotNull {
        it.deficit.toDoubleOrNull()
    }

    val prevDef = allMetrics.filter {
        prevWeekDates.contains(it.date)
    }.mapNotNull {
        it.deficit.toDoubleOrNull()
    }

    val curKcalAvg = if (currentKcal.isNotEmpty()) currentKcal.average().toInt() else 0
    val prevKcalAvg = if (prevKcal.isNotEmpty()) prevKcal.average().toInt() else 0
    val curDefAvg = if (currentDef.isNotEmpty()) currentDef.average().toInt() else 0
    val prevDefAvg = if (prevDef.isNotEmpty()) prevDef.average().toInt() else 0

    val diffKcal = curKcalAvg - prevKcalAvg
    val diffDef = curDefAvg - prevDefAvg

    val tagStats = remember(allTags, allMetrics, phasePreference, customTags) {
        val metricsMap = allMetrics.associateBy { it.date }
        val tagMap = mutableMapOf<String, MutableList<DailyMetricEntity>>()

        allTags.forEach { tagEntity ->
            val dayTags = tagEntity.tags.split(",")
                .map { it.trim() }
                .filter { tagString ->
                    tagString.isNotBlank() &&
                            !tagString.startsWith("Friction:", ignoreCase = true) &&
                            !tagString.startsWith("Sleep:", ignoreCase = true) &&
                            customTags.contains(tagString)
                }

            val metricForDay = metricsMap[tagEntity.date]

            if (metricForDay != null) {
                dayTags.forEach { tag ->
                    tagMap.getOrPut(tag) { mutableListOf() }.add(metricForDay)
                }
            }
        }

        tagMap.map { (tag, metrics) ->
            val totalDays = metrics.size
            val successDays = metrics.count { metric ->
                val def = metric.deficit.toDoubleOrNull() ?: 0.0
                if (phasePreference == "bulk") def < 0 else def > 0
            }
            val winRate = if (totalDays > 0) (successDays.toFloat() / totalDays * 100).toInt() else 0
            val avgDeficit = if (totalDays > 0) metrics.mapNotNull { it.deficit.toDoubleOrNull() }.average().toInt() else 0

            TagStat(
                tag = tag,
                totalDays = totalDays,
                winRate = winRate,
                avgDeficit = avgDeficit
            )
        }.sortedByDescending { it.totalDays }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        generateExecutiveSummaryPdf(
                            outputStream,
                            phasePreference,
                            curKcalAvg,
                            curDefAvg,
                            tagStats,
                            last14Days,
                            allMetrics,
                            allMeasurements
                        )
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Executive Summary Saved!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
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

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }

    val displayKcal by animateIntAsState(
        targetValue = curKcalAvg,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "kcalTicker"
    )
    val displayDef by animateIntAsState(
        targetValue = curDefAvg,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "defTicker"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "aura")
    val auraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPhase"
    )

    val isSuccess = if (phasePreference == "bulk") curDefAvg <= 0 else curDefAvg >= 0
    val baseContainer = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    val auraBrush = Brush.linearGradient(
        colors = listOf(
            baseContainer,
            highlightColor.copy(alpha = 0.15f + (0.1f * auraPhase))
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 1000f)
    )

    val typeWeightKcal = (100 + ((displayKcal.toFloat() / (curKcalAvg.takeIf { it > 0 } ?: 1)) * 700)).toInt().coerceIn(100, 800)
    val typeWeightDef = (100 + ((abs(displayDef).toFloat() / (abs(curDefAvg).takeIf { it > 0 } ?: 1)) * 700)).toInt().coerceIn(100, 800)

    var showTopCardsTooltip by remember { mutableStateOf(false) }
    var showHeatmapTooltip by remember { mutableStateOf(false) }
    var showMacroTooltip by remember { mutableStateOf(false) }
    var showCompTooltip by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Analytics Dashboard") },
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
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            exportPdfLauncher.launch("Gryzor_Summary_${LocalDate.now()}.pdf")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Assessment,
                            contentDescription = "Export PDF Report"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { safeInsets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = safeInsets.calculateTopPadding(),
                    bottom = safeInsets.calculateBottomPadding()
                ),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            fun elasticMod(index: Int) = Modifier
                .offset(y = (40.dp * (1f - entrance.value) * (index + 1)))
                .alpha(entrance.value)

            // --- 1. TOP METRIC CARDS ---
            item {
                Column(modifier = elasticMod(0).fillMaxWidth().padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Velocity & Trajectory",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showTopCardsTooltip = !showTopCardsTooltip
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QuestionMark,
                                contentDescription = "Info",
                                modifier = Modifier.padding(3.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = showTopCardsTooltip) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "Compares your trailing 7-day average against the previous 7-day period to show acceleration or deceleration of habits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    rotationX = (gyroPitch * 0.15f).coerceIn(-8f, 8f)
                                    rotationY = (gyroRoll * 0.15f).coerceIn(-8f, 8f)
                                    cameraDistance = 12f * density
                                },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(modifier = Modifier.background(auraBrush).padding(16.dp)) {
                                Column {
                                    Text(
                                        text = "7-Day Avg Intake",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$displayKcal",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight(typeWeightKcal)
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val arrowKcal = if (diffKcal > 0) "↑" else if (diffKcal < 0) "↓" else "="
                                    val colorKcal = if (diffKcal > 0) MaterialTheme.colorScheme.error else if (diffKcal < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

                                    Text(
                                        text = "$arrowKcal ${abs(diffKcal)} vs last wk",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorKcal
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    rotationX = (gyroPitch * 0.15f).coerceIn(-8f, 8f)
                                    rotationY = (gyroRoll * 0.15f).coerceIn(-8f, 8f)
                                    cameraDistance = 12f * density
                                },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(modifier = Modifier.background(auraBrush).padding(16.dp)) {
                                Column {
                                    Text(
                                        text = if (phasePreference == "bulk") "7-Day Avg Surplus" else "7-Day Avg Deficit",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$displayDef",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight(typeWeightDef)
                                        ),
                                        color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )

                                    val arrowDef = if (diffDef > 0) "↑" else if (diffDef < 0) "↓" else "="
                                    val colorDef = if (phasePreference == "cut") {
                                        if (diffDef < 0) MaterialTheme.colorScheme.primary else if (diffDef > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                    } else {
                                        if (diffDef > 0) MaterialTheme.colorScheme.error else if (diffDef < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    }

                                    Text(
                                        text = "$arrowDef ${abs(diffDef)} vs last wk",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorDef
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 2. CONSISTENCY HEATMAP ---
            item {
                val last30Days = (29 downTo 0).map { today.minusDays(it.toLong()).toString() }

                Column(modifier = elasticMod(1).fillMaxWidth().padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Consistency Heatmap",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showHeatmapTooltip = !showHeatmapTooltip
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QuestionMark,
                                contentDescription = "Info",
                                modifier = Modifier.padding(3.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Last 30 days. Tap a day to view log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    AnimatedVisibility(visible = showHeatmapTooltip) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = "A visual representation of adherence over a rolling 30-day window. The Primary Theme Color indicates a successful day based on your phase (Cut/Bulk). The Error Color indicates failure, and the subdued Surface Color indicates no data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            last30Days.forEach { date ->
                                val metric = allMetrics.find { it.date == date }
                                val defValue = metric?.deficit?.toDoubleOrNull()
                                val isDaySuccess = if (phasePreference == "bulk") (defValue ?: 0.0) < 0 else (defValue ?: 0.0) > 0

                                val boxColor = if (defValue == null || defValue == 0.0) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                } else if (isDaySuccess) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(boxColor)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            navController.previousBackStackEntry?.savedStateHandle?.set("targetDate", date)
                                            navController.popBackStack()
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // --- 3. MACRO TREND GRAPH ---
            item {
                val primaryColor = MaterialTheme.colorScheme.primary
                val errorColor = MaterialTheme.colorScheme.error
                val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
                val dateStyle = TextStyle(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                val axisStyleKcal = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor.copy(alpha = 0.8f)
                )
                val axisStyleDef = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = errorColor.copy(alpha = 0.8f)
                )
                val tooltipStyle = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                var tappedMacro by remember { mutableStateOf<Triple<Offset, String, Color>?>(null) }
                var showSignalOverlay by remember { mutableStateOf(false) }
                var lastMacroHapticIndex by remember { mutableIntStateOf(-1) }

                Column(modifier = elasticMod(2).fillMaxWidth().padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 32.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "31-Day Macro Trend",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showMacroTooltip = !showMacroTooltip
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QuestionMark,
                                    contentDescription = "Info",
                                    modifier = Modifier.padding(3.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSignalOverlay = false
                                    }
                                    .background(if (!showSignalOverlay) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Raw",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!showSignalOverlay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSignalOverlay = true
                                    }
                                    .background(if (showSignalOverlay) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Signal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (showSignalOverlay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "Drag to scrub. Primary = Intake, Red = Def.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    AnimatedVisibility(visible = showMacroTooltip) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = "Interactive vector graph. Drag your finger to scrub. Tooltips calculate the delta against the previous day's value to highlight local momentum.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    data class MacroNode(
                        val date: String,
                        val rawKcal: Float?,
                        val rawDef: Float?,
                        val avgKcal: Float?,
                        val avgDef: Float?
                    )

                    val macroNodes = remember(last31Days, allMetrics) {
                        last31Days.map { date ->
                            val metric = allMetrics.find { it.date == date }
                            val rK = metric?.totalKcal?.toFloatOrNull()
                            val rD = metric?.deficit?.toFloatOrNull()

                            val window = (6 downTo 0).map { LocalDate.parse(date).minusDays(it.toLong()).toString() }
                            val windowMetrics = allMetrics.filter { window.contains(it.date) }

                            val kList = windowMetrics.mapNotNull { it.totalKcal.toDoubleOrNull() }
                            val dList = windowMetrics.mapNotNull { it.deficit.toDoubleOrNull() }

                            val aK = if (kList.isNotEmpty()) kList.average().toFloat() else null
                            val aD = if (dList.isNotEmpty()) dList.average().toFloat() else null

                            MacroNode(date, rK, rD, aK, aD)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(macroNodes, showSignalOverlay) {
                                        awaitEachGesture {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val isPressed = event.changes.any { it.pressed }
                                                if (isPressed) {
                                                    val position = event.changes.first().position
                                                    val stepX = size.width / 30f
                                                    val paddingBottom = 40f
                                                    val graphHeight = size.height - paddingBottom

                                                    val allK = macroNodes.mapNotNull { it.rawKcal } + macroNodes.mapNotNull { it.avgKcal }
                                                    val allD = macroNodes.mapNotNull { it.rawDef } + macroNodes.mapNotNull { it.avgDef }

                                                    val maxKcal = allK.maxOrNull()?.coerceAtLeast(2500f) ?: 2500f
                                                    val minDeficit = allD.minOrNull()?.coerceAtMost(0f) ?: -500f
                                                    val totalRange = maxKcal - minDeficit

                                                    val cIndex = (position.x / stepX).roundToInt().coerceIn(0, 30)

                                                    if (cIndex != lastMacroHapticIndex) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        lastMacroHapticIndex = cIndex
                                                    }

                                                    val node = macroNodes[cIndex]
                                                    val prevNode = if (cIndex > 0) macroNodes[cIndex - 1] else null
                                                    val xPos = cIndex * stepX

                                                    val yPoints = mutableListOf<Triple<Offset, String, Color>>()

                                                    val kcalToUse = if (showSignalOverlay && node.avgKcal != null) node.avgKcal else node.rawKcal
                                                    val prevKcal = if (showSignalOverlay && prevNode?.avgKcal != null) prevNode.avgKcal else prevNode?.rawKcal

                                                    if (kcalToUse != null) {
                                                        val deltaStr = if (prevKcal != null) {
                                                            val d = kcalToUse - prevKcal
                                                            val sign = if (d > 0) "↑" else if (d < 0) "↓" else "="
                                                            " ($sign ${abs(d).toInt()})"
                                                        } else ""
                                                        yPoints.add(Triple(Offset(xPos, graphHeight - ((kcalToUse - minDeficit) / totalRange) * graphHeight), "${kcalToUse.toInt()} In$deltaStr", primaryColor))
                                                    }

                                                    val defToUse = if (showSignalOverlay && node.avgDef != null) node.avgDef else node.rawDef
                                                    val prevDef = if (showSignalOverlay && prevNode?.avgDef != null) prevNode.avgDef else prevNode?.rawDef

                                                    if (defToUse != null) {
                                                        val deltaStr = if (prevDef != null) {
                                                            val d = defToUse - prevDef
                                                            val sign = if (d > 0) "↑" else if (d < 0) "↓" else "="
                                                            " ($sign ${abs(d).toInt()})"
                                                        } else ""
                                                        yPoints.add(Triple(Offset(xPos, graphHeight - ((defToUse - minDeficit) / totalRange) * graphHeight), "${defToUse.toInt()} Def$deltaStr", errorColor))
                                                    }

                                                    tappedMacro = yPoints.minByOrNull { abs(it.first.y - position.y) }
                                                    event.changes.first().consume()
                                                } else {
                                                    lastMacroHapticIndex = -1
                                                }
                                            }
                                        }
                                    }
                            ) {
                                val stepX = size.width / 30f
                                val paddingBottom = 40f
                                val graphHeight = size.height - paddingBottom

                                val allK = macroNodes.mapNotNull { it.rawKcal } + macroNodes.mapNotNull { it.avgKcal }
                                val allD = macroNodes.mapNotNull { it.rawDef } + macroNodes.mapNotNull { it.avgDef }

                                val maxKcal = allK.maxOrNull()?.coerceAtLeast(2500f) ?: 2500f
                                val minKcal = allK.minOrNull() ?: 0f
                                val minDeficit = allD.minOrNull()?.coerceAtMost(0f) ?: -500f
                                val maxDeficit = allD.maxOrNull() ?: 0f
                                val totalRange = maxKcal - minDeficit

                                macroNodes.forEachIndexed { index, node ->
                                    val date = node.date
                                    val tagsForDate = allTags.find { it.date == date }?.tags ?: ""

                                    if (tagsForDate.contains("Grind", ignoreCase = true)) {
                                        drawRoundRect(
                                            color = surfaceColor.copy(alpha = animProgress.value),
                                            topLeft = Offset(index * stepX - (stepX / 2f), 0f),
                                            size = Size(stepX, graphHeight),
                                            cornerRadius = CornerRadius(8f, 8f)
                                        )
                                    }

                                    if (index % 6 == 0 || index == 30) {
                                        val layoutResult = textMeasurer.measure(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M/d")), dateStyle)
                                        drawText(
                                            textLayoutResult = layoutResult,
                                            topLeft = Offset(index * stepX - (layoutResult.size.width / 2f), size.height - 20f)
                                        )
                                    }
                                }

                                val zeroY = graphHeight - ((0 - minDeficit) / totalRange) * graphHeight
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    start = Offset(0f, zeroY),
                                    end = Offset(size.width, zeroY),
                                    strokeWidth = 2f
                                )

                                if (animProgress.value > 0.8f && totalRange > 0) {
                                    val maxKcalLabel = textMeasurer.measure("${maxKcal.toInt()} In", axisStyleKcal)
                                    drawText(
                                        textLayoutResult = maxKcalLabel,
                                        topLeft = Offset(0f, 0f)
                                    )

                                    if (minKcal < maxKcal && minKcal > 0) {
                                        val minKcalLabel = textMeasurer.measure("${minKcal.toInt()} In", axisStyleKcal)
                                        val minKcalY = graphHeight - ((minKcal - minDeficit) / totalRange) * graphHeight
                                        drawText(
                                            textLayoutResult = minKcalLabel,
                                            topLeft = Offset(0f, minKcalY - minKcalLabel.size.height)
                                        )
                                    }

                                    val minDefLabel = textMeasurer.measure("${minDeficit.toInt()} Def", axisStyleDef)
                                    drawText(
                                        textLayoutResult = minDefLabel,
                                        topLeft = Offset(size.width - minDefLabel.size.width, graphHeight - minDefLabel.size.height)
                                    )

                                    if (maxDeficit > minDeficit) {
                                        val maxDefLabel = textMeasurer.measure("${maxDeficit.toInt()} Def", axisStyleDef)
                                        val maxDefY = graphHeight - ((maxDeficit - minDeficit) / totalRange) * graphHeight
                                        drawText(
                                            textLayoutResult = maxDefLabel,
                                            topLeft = Offset(size.width - maxDefLabel.size.width, maxDefY)
                                        )
                                    }
                                }

                                val rawKcalPath = Path()
                                val rawDefPath = Path()
                                val avgKcalPath = Path()
                                val avgDefPath = Path()
                                val rawKcalArea = Path()
                                val rawDefArea = Path()

                                var firstRawK = true
                                var firstRawD = true
                                var firstAvgK = true
                                var firstAvgD = true
                                var lastRawKX = 0f
                                var lastRawDX = 0f

                                macroNodes.forEachIndexed { index, node ->
                                    val x = index * stepX
                                    if (node.rawKcal != null) {
                                        val y = zeroY + ((graphHeight - ((node.rawKcal - minDeficit) / totalRange) * graphHeight) - zeroY) * animProgress.value
                                        lastRawKX = x
                                        if (firstRawK) {
                                            rawKcalPath.moveTo(x, y)
                                            rawKcalArea.moveTo(x, graphHeight)
                                            rawKcalArea.lineTo(x, y)
                                            firstRawK = false
                                        } else {
                                            rawKcalPath.lineTo(x, y)
                                            rawKcalArea.lineTo(x, y)
                                        }
                                        drawCircle(color = primaryColor.copy(alpha = animProgress.value), radius = 6f, center = Offset(x, y))
                                    }

                                    if (node.rawDef != null) {
                                        val y = zeroY + ((graphHeight - ((node.rawDef - minDeficit) / totalRange) * graphHeight) - zeroY) * animProgress.value
                                        lastRawDX = x
                                        if (firstRawD) {
                                            rawDefPath.moveTo(x, y)
                                            rawDefArea.moveTo(x, zeroY)
                                            rawDefArea.lineTo(x, y)
                                            firstRawD = false
                                        } else {
                                            rawDefPath.lineTo(x, y)
                                            rawDefArea.lineTo(x, y)
                                        }
                                        drawCircle(color = errorColor.copy(alpha = animProgress.value), radius = 6f, center = Offset(x, y))
                                    }

                                    if (node.avgKcal != null) {
                                        val y = zeroY + ((graphHeight - ((node.avgKcal - minDeficit) / totalRange) * graphHeight) - zeroY) * animProgress.value
                                        if (firstAvgK) { avgKcalPath.moveTo(x, y); firstAvgK = false }
                                        else { avgKcalPath.lineTo(x, y) }
                                    }

                                    if (node.avgDef != null) {
                                        val y = zeroY + ((graphHeight - ((node.avgDef - minDeficit) / totalRange) * graphHeight) - zeroY) * animProgress.value
                                        if (firstAvgD) { avgDefPath.moveTo(x, y); firstAvgD = false }
                                        else { avgDefPath.lineTo(x, y) }
                                    }
                                }

                                if (!firstRawK) {
                                    rawKcalArea.lineTo(lastRawKX, graphHeight)
                                    rawKcalArea.close()
                                    drawPath(path = rawKcalArea, brush = Brush.verticalGradient(colors = listOf(primaryColor.copy(alpha = 0.2f * animProgress.value), Color.Transparent), startY = 0f, endY = graphHeight))
                                }

                                if (!firstRawD) {
                                    rawDefArea.lineTo(lastRawDX, zeroY)
                                    rawDefArea.close()
                                    drawPath(path = rawDefArea, brush = Brush.verticalGradient(colors = listOf(errorColor.copy(alpha = 0.2f * animProgress.value), Color.Transparent), startY = 0f, endY = graphHeight))
                                }

                                drawPath(path = rawKcalPath, color = primaryColor.copy(alpha = animProgress.value), style = Stroke(width = 3f, cap = StrokeCap.Round))
                                drawPath(path = rawDefPath, color = errorColor.copy(alpha = animProgress.value), style = Stroke(width = 3f, cap = StrokeCap.Round))

                                if (showSignalOverlay) {
                                    drawPath(path = avgKcalPath, color = primaryColor.copy(alpha = 0.6f * animProgress.value), style = Stroke(width = 5f, cap = StrokeCap.Round))
                                    drawPath(path = avgDefPath, color = errorColor.copy(alpha = 0.6f * animProgress.value), style = Stroke(width = 5f, cap = StrokeCap.Round))
                                }

                                tappedMacro?.let { (offset, text, color) ->
                                    val textLayout = textMeasurer.measure(text, tooltipStyle)
                                    val tWidth = textLayout.size.width + 24f
                                    val tHeight = textLayout.size.height + 16f
                                    var tX = offset.x - tWidth / 2
                                    if (tX < 0f) tX = 0f
                                    if (tX + tWidth > size.width) tX = size.width - tWidth
                                    var tY = offset.y - tHeight - 48f
                                    if (tY < 0f) tY = offset.y + 48f

                                    drawRoundRect(color = color, topLeft = Offset(tX, tY), size = Size(tWidth, tHeight), cornerRadius = CornerRadius(12f, 12f))
                                    drawText(textLayoutResult = textLayout, topLeft = Offset(tX + 12f, tY + 8f))
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. BODY COMP TREND GRAPH (V4.82 SPLIT PANE) ---
            item {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secColor = MaterialTheme.colorScheme.secondary
                val dateStyle = TextStyle(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                val axisStyleW = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor.copy(alpha = 0.8f)
                )
                val axisStyleF = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = secColor.copy(alpha = 0.8f)
                )

                val tooltipStyle = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                var tappedComp by remember { mutableStateOf<Triple<Offset, String, Color>?>(null) }
                var lastCompHapticIndex by remember { mutableIntStateOf(-1) }

                Column(modifier = elasticMod(3).fillMaxWidth().padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Body Composition Trend",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCompTooltip = !showCompTooltip
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QuestionMark,
                                contentDescription = "Info",
                                modifier = Modifier.padding(3.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Split-pane bounds to prevent line crossing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    AnimatedVisibility(visible = showCompTooltip) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = "Interactive drag-to-scrub vector graph. The canvas is segmented: the top 47.5% is dedicated entirely to Weight variance, and the bottom 47.5% is dedicated to Body Fat variance, ensuring the trend lines never visually cross.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val dynamicBounds = remember(last31Days, allMeasurements) {
                        val wList = last31Days.mapNotNull { d -> allMeasurements.find { it.date == d }?.weight?.toFloatOrNull() }
                        val fList = last31Days.mapNotNull { d -> allMeasurements.find { it.date == d }?.bodyFat?.toFloatOrNull() }

                        val wMaxRaw = wList.maxOrNull() ?: 85f
                        val wMinRaw = wList.minOrNull() ?: 66f
                        val fMaxRaw = fList.maxOrNull() ?: 25f
                        val fMinRaw = fList.minOrNull() ?: 10f

                        val wMax = if (wMaxRaw == wMinRaw) wMaxRaw + 1f else wMaxRaw
                        val wMin = if (wMaxRaw == wMinRaw) wMinRaw - 1f else wMinRaw
                        val fMax = if (fMaxRaw == fMinRaw) fMaxRaw + 1f else fMaxRaw
                        val fMin = if (fMaxRaw == fMinRaw) fMinRaw - 1f else fMinRaw

                        listOf(wMax, wMin, fMax, fMin)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(allMeasurements, dynamicBounds) {
                                        awaitEachGesture {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val isPressed = event.changes.any { it.pressed }
                                                if (isPressed) {
                                                    val position = event.changes.first().position
                                                    val stepX = size.width / 30f
                                                    val paddingBottom = 40f
                                                    val graphHeight = size.height - paddingBottom

                                                    // V4.82 Split Pane Math
                                                    val wRangeHeight = graphHeight * 0.475f
                                                    val fRangeHeight = graphHeight * 0.475f
                                                    val fStartY = graphHeight

                                                    val maxWeight = dynamicBounds[0]
                                                    val minWeight = dynamicBounds[1]
                                                    val maxFat = dynamicBounds[2]
                                                    val minFat = dynamicBounds[3]

                                                    val rangeW = maxWeight - minWeight
                                                    val rangeF = maxFat - minFat

                                                    val cIndex = (position.x / stepX).roundToInt().coerceIn(0, 30)

                                                    if (cIndex != lastCompHapticIndex) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        lastCompHapticIndex = cIndex
                                                    }

                                                    val date = last31Days[cIndex]
                                                    val prevDate = if (cIndex > 0) last31Days[cIndex - 1] else null

                                                    val measure = allMeasurements.find { it.date == date }
                                                    val prevMeasure = if (prevDate != null) allMeasurements.find { it.date == prevDate } else null

                                                    val xPos = cIndex * stepX
                                                    val yPoints = mutableListOf<Triple<Offset, String, Color>>()

                                                    val w = measure?.weight?.toFloatOrNull()
                                                    val pW = prevMeasure?.weight?.toFloatOrNull()
                                                    if (w != null) {
                                                        val deltaStr = if (pW != null) {
                                                            val d = w - pW
                                                            val sign = if (d > 0) "↑" else if (d < 0) "↓" else "="
                                                            " ($sign ${String.format("%.1f", abs(d))})"
                                                        } else ""
                                                        val wY = wRangeHeight - ((w.coerceIn(minWeight, maxWeight) - minWeight) / rangeW) * wRangeHeight
                                                        yPoints.add(Triple(Offset(xPos, wY), "${w}kg$deltaStr", primaryColor))
                                                    }

                                                    val f = measure?.bodyFat?.toFloatOrNull()
                                                    val pF = prevMeasure?.bodyFat?.toFloatOrNull()
                                                    if (f != null) {
                                                        val deltaStr = if (pF != null) {
                                                            val d = f - pF
                                                            val sign = if (d > 0) "↑" else if (d < 0) "↓" else "="
                                                            " ($sign ${String.format("%.1f", abs(d))})"
                                                        } else ""
                                                        val fY = fStartY - ((f.coerceIn(minFat, maxFat) - minFat) / rangeF) * fRangeHeight
                                                        yPoints.add(Triple(Offset(xPos, fY), "${f}%$deltaStr", secColor))
                                                    }

                                                    tappedComp = yPoints.minByOrNull { abs(it.first.y - position.y) }
                                                    event.changes.first().consume()
                                                } else {
                                                    lastCompHapticIndex = -1
                                                }
                                            }
                                        }
                                    }
                            ) {
                                val stepX = size.width / 30f
                                val paddingBottom = 40f
                                val graphHeight = size.height - paddingBottom

                                // V4.82 Split Pane Math Constants
                                val wRangeHeight = graphHeight * 0.475f
                                val fRangeHeight = graphHeight * 0.475f
                                val fStartY = graphHeight

                                val maxWeight = dynamicBounds[0]
                                val minWeight = dynamicBounds[1]
                                val maxFat = dynamicBounds[2]
                                val minFat = dynamicBounds[3]

                                val rangeW = maxWeight - minWeight
                                val rangeF = maxFat - minFat

                                if (animProgress.value > 0.8f) {
                                    val wFormat = if (maxWeight % 1 == 0f) maxWeight.toInt().toString() else maxWeight.toString()
                                    val wMinFormat = if (minWeight % 1 == 0f) minWeight.toInt().toString() else minWeight.toString()

                                    val maxWLabel = textMeasurer.measure("${wFormat}kg", axisStyleW)
                                    drawText(textLayoutResult = maxWLabel, topLeft = Offset(0f, 0f))

                                    val minWLabel = textMeasurer.measure("${wMinFormat}kg", axisStyleW)
                                    drawText(textLayoutResult = minWLabel, topLeft = Offset(0f, wRangeHeight - minWLabel.size.height))

                                    val fFormat = if (maxFat % 1 == 0f) maxFat.toInt().toString() else maxFat.toString()
                                    val fMinFormat = if (minFat % 1 == 0f) minFat.toInt().toString() else minFat.toString()

                                    val maxFLabel = textMeasurer.measure("${fFormat}%", axisStyleF)
                                    drawText(textLayoutResult = maxFLabel, topLeft = Offset(size.width - maxFLabel.size.width, graphHeight * 0.525f))

                                    val minFLabel = textMeasurer.measure("${fMinFormat}%", axisStyleF)
                                    drawText(textLayoutResult = minFLabel, topLeft = Offset(size.width - minFLabel.size.width, graphHeight - minFLabel.size.height))
                                }

                                val wPath = Path()
                                val fPath = Path()
                                val wAreaPath = Path()
                                val fAreaPath = Path()
                                var firstW = true
                                var firstF = true
                                var lastWX = 0f
                                var lastFX = 0f

                                last31Days.forEachIndexed { index, date ->
                                    if (index % 6 == 0 || index == 30) {
                                        val layoutResult = textMeasurer.measure(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M/d")), dateStyle)
                                        drawText(textLayoutResult = layoutResult, topLeft = Offset(index * stepX - (layoutResult.size.width / 2f), size.height - 20f))
                                    }

                                    val measure = allMeasurements.find { it.date == date }
                                    val x = index * stepX

                                    val w = measure?.weight?.toFloatOrNull()
                                    if (w != null) {
                                        val targetY = wRangeHeight - ((w.coerceIn(minWeight, maxWeight) - minWeight) / rangeW) * wRangeHeight
                                        val y = wRangeHeight + (targetY - wRangeHeight) * animProgress.value
                                        lastWX = x
                                        if (firstW) { wPath.moveTo(x, y); wAreaPath.moveTo(x, wRangeHeight); wAreaPath.lineTo(x, y); firstW = false }
                                        else { wPath.lineTo(x, y); wAreaPath.lineTo(x, y) }
                                        drawCircle(color = primaryColor.copy(alpha = animProgress.value), radius = 6f, center = Offset(x, y))
                                    }

                                    val f = measure?.bodyFat?.toFloatOrNull()
                                    if (f != null) {
                                        val targetY = fStartY - ((f.coerceIn(minFat, maxFat) - minFat) / rangeF) * fRangeHeight
                                        val y = fStartY + (targetY - fStartY) * animProgress.value
                                        lastFX = x
                                        if (firstF) { fPath.moveTo(x, y); fAreaPath.moveTo(x, graphHeight); fAreaPath.lineTo(x, y); firstF = false }
                                        else { fPath.lineTo(x, y); fAreaPath.lineTo(x, y) }
                                        drawCircle(color = secColor.copy(alpha = animProgress.value), radius = 6f, center = Offset(x, y))
                                    }
                                }

                                if (!firstW) {
                                    wAreaPath.lineTo(lastWX, wRangeHeight)
                                    wAreaPath.close()
                                    drawPath(path = wAreaPath, brush = Brush.verticalGradient(colors = listOf(primaryColor.copy(alpha = 0.15f * animProgress.value), Color.Transparent), startY = 0f, endY = wRangeHeight))
                                }

                                if (!firstF) {
                                    fAreaPath.lineTo(lastFX, graphHeight)
                                    fAreaPath.close()
                                    drawPath(path = fAreaPath, brush = Brush.verticalGradient(colors = listOf(secColor.copy(alpha = 0.15f * animProgress.value), Color.Transparent), startY = graphHeight * 0.525f, endY = graphHeight))
                                }

                                drawPath(path = wPath, color = primaryColor.copy(alpha = animProgress.value), style = Stroke(width = 3f, cap = StrokeCap.Round))
                                drawPath(path = fPath, color = secColor.copy(alpha = animProgress.value), style = Stroke(width = 3f, cap = StrokeCap.Round))

                                tappedComp?.let { (offset, text, color) ->
                                    val textLayout = textMeasurer.measure(text, tooltipStyle)
                                    val tWidth = textLayout.size.width + 24f
                                    val tHeight = textLayout.size.height + 16f
                                    var tX = offset.x - tWidth / 2
                                    if (tX < 0f) tX = 0f
                                    if (tX + tWidth > size.width) tX = size.width - tWidth
                                    var tY = offset.y - tHeight - 48f
                                    if (tY < 0f) tY = offset.y + 48f

                                    drawRoundRect(color = color, topLeft = Offset(tX, tY), size = Size(tWidth, tHeight), cornerRadius = CornerRadius(12f, 12f))
                                    drawText(textLayoutResult = textLayout, topLeft = Offset(tX + 12f, tY + 8f))
                                }
                            }
                        }
                    }
                }
            }

            // --- 5. BEHAVIORAL COMPLIANCE MATRIX ---
            item {
                var showTooltip by remember { mutableStateOf(false) }

                if (tagStats.isNotEmpty()) {
                    Column(modifier = elasticMod(4).fillMaxWidth().padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 0.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Behavioral Compliance Matrix",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTooltip = !showTooltip
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QuestionMark,
                                    contentDescription = "How is this calculated?",
                                    modifier = Modifier.padding(3.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = showTooltip) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
                            ) {
                                val successText = if (phasePreference == "bulk") "Surplus (< 0)" else "Deficit (> 0)"
                                Text(
                                    text = "Success Rate = The percentage of days with this tag where you successfully logged a Caloric $successText.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                tagStats.forEachIndexed { index, stat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stat.tag,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${stat.totalDays} days logged",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            val rateColor = if (stat.winRate >= 70) MaterialTheme.colorScheme.primary else if (stat.winRate >= 40) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

                                            Text(
                                                text = "${stat.winRate}% Success",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = rateColor
                                            )
                                            Text(
                                                text = "Avg Def: ${stat.avgDeficit}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    if (index < tagStats.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}