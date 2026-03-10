package com.example.gryzorfoodtracker

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

fun parseVoiceInput(
    spokenText: String,
    defaultTime: String
): Triple<String, String, String> {
    val lower = spokenText.lowercase()
    val type = when {
        "breakfast" in lower -> "Breakfast"
        "lunch" in lower -> "Lunch"
        "dinner" in lower -> "Dinner"
        "snack" in lower -> "Snack"
        else -> "Snack"
    }

    var parsedTime = defaultTime
    val timeRegex = Regex(
        pattern = "\\b([1-9]|1[0-2])(?:[:.]([0-5][0-9]))?\\s*(am|pm)\\b",
        option = RegexOption.IGNORE_CASE
    )
    val match = timeRegex.find(lower)

    if (match != null) {
        val hourStr = match.groups[1]?.value ?: "12"
        val minStr = match.groups[2]?.value ?: "00"
        val ampm = match.groups[3]?.value?.lowercase() ?: "am"
        var hour = hourStr.toInt()

        if (ampm == "pm" && hour < 12) {
            hour += 12
        }
        if (ampm == "am" && hour == 12) {
            hour = 0
        }

        parsedTime = String.format("%02d:%02d", hour, minStr.toInt())
    }

    val typeRegex = Regex("(?i)\\b(breakfast|lunch|dinner|snack)\\b[,\\s]*")
    val cleanedDesc = spokenText.replace(typeRegex, "").trim().replaceFirstChar { it.uppercase() }

    return Triple(type, parsedTime, cleanedDesc)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodTrackerScreen(
    db: AppDatabase,
    themePreference: String,
    navController: NavController,
    shortcutMealType: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val dao = db.mealDao()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    val (gyroPitch, gyroRoll) = rememberGyroscopeTilt()

    var editingMeal by remember { mutableStateOf<MealEntity?>(null) }
    var duplicatingMeal by remember { mutableStateOf<MealEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    var initialDialogMealType by remember { mutableStateOf<String?>("Lunch") }
    var initialDialogTime by remember { mutableStateOf<String?>(null) }
    var initialDialogDesc by remember { mutableStateOf<String?>(null) }

    var handledShortcut by rememberSaveable(shortcutMealType) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val initialPage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { Int.MAX_VALUE }
    )

    val currentBackStackEntry = navController.currentBackStackEntry
    val targetDateStr by currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("targetDate", null)?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(targetDateStr) {
        if (!targetDateStr.isNullOrBlank()) {
            try {
                val targetDate = LocalDate.parse(targetDateStr)
                val daysDiff = ChronoUnit.DAYS.between(LocalDate.now(), targetDate).toInt()
                val targetPage = initialPage + daysDiff
                if (pagerState.currentPage != targetPage) {
                    pagerState.animateScrollToPage(targetPage)
                }
                currentBackStackEntry?.savedStateHandle?.remove<String>("targetDate")
            } catch (e: Exception) {}
        }
    }

    val currentDate = remember(pagerState.currentPage) {
        LocalDate.now().plusDays((pagerState.currentPage - initialPage).toLong())
    }

    val currentDayEntries by dao.getMealsForDate(currentDate.toString()).collectAsState(initial = emptyList())
    val currentDayTags by dao.getTagsForDate(currentDate.toString()).collectAsState(initial = null)
    val currentDayInsight by dao.getInsightForDate(currentDate.toString()).collectAsState(initial = null)
    val currentDayMetrics by dao.getMetricsForDate(currentDate.toString()).collectAsState(initial = null)
    val currentDayMeasurement by dao.getMeasurementForDate(currentDate.toString()).collectAsState(initial = null)

    val availableTags by context.dataStore.data.map { it[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS }.collectAsState(DEFAULT_TAGS)
    val bannedSuggestions by context.dataStore.data.map { it[BANNED_SUGGESTIONS_KEY] ?: emptySet() }.collectAsState(emptySet())

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentDate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { MacroWidget().updateAll(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = data?.get(0) ?: ""
            val (parsedType, parsedTime, parsedDesc) = parseVoiceInput(
                spokenText = spokenText,
                defaultTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            )
            initialDialogMealType = parsedType
            initialDialogTime = parsedTime
            initialDialogDesc = parsedDesc
            editingMeal = null
            duplicatingMeal = null
            showAddDialog = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            speechRecognizerLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Microphone permission required for voice input.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(shortcutMealType) {
        if (shortcutMealType != null && !handledShortcut) {
            initialDialogMealType = shortcutMealType
            initialDialogTime = null
            initialDialogDesc = null
            editingMeal = null
            duplicatingMeal = null
            showAddDialog = true
            handledShortcut = true
        }
    }

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(currentDate) {
        entrance.snapTo(0f)
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f)
        )
    }

    val appScale by animateFloatAsState(
        targetValue = if (showAddDialog) 0.94f else 1f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "scale"
    )
    val appBlur by animateDpAsState(
        targetValue = if (showAddDialog) 16.dp else 0.dp,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "blur"
    )

    val headerTagsStr = currentDayTags?.tags ?: ""
    val headerTagsList = headerTagsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

    val headerFriction = headerTagsList.find { it.startsWith("Friction:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
    val headerSleep = headerTagsList.find { it.startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier
                .scale(appScale)
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(if (showAddDialog) 32.dp else 0.dp)
                }
                .blur(
                    radius = appBlur,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                LargeDayHeader(
                    date = currentDate,
                    dailyKcal = currentDayMetrics?.totalKcal,
                    dailyDeficit = currentDayMetrics?.deficit,
                    dailyWeight = currentDayMeasurement?.weight,
                    dailyFat = currentDayMeasurement?.bodyFat,
                    frictionScore = headerFriction,
                    onFrictionChange = { newF ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val cleaned = headerTagsList.filter { !it.startsWith("Friction:") }
                            val newTags = if (newF > 0) cleaned + "Friction: $newF" else cleaned
                            dao.insertTags(
                                DailyTagEntity(
                                    date = currentDate.toString(),
                                    tags = newTags.joinToString(",")
                                )
                            )
                            MacroWidget().updateAll(context)
                        }
                    },
                    sleepScore = headerSleep,
                    onSleepChange = { newS ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val cleaned = headerTagsList.filter { !it.startsWith("Sleep:") }
                            val newTags = if (newS > 0) cleaned + "Sleep: $newS" else cleaned
                            dao.insertTags(
                                DailyTagEntity(
                                    date = currentDate.toString(),
                                    tags = newTags.joinToString(",")
                                )
                            )
                            MacroWidget().updateAll(context)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    onBehaviorClick = {
                        navController.navigate("behavior")
                    },
                    onAnalyticsClick = {
                        navController.navigate("analytics")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    },
                    onPrev = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            entries = currentDayEntries,
                            date = currentDate,
                            tags = currentDayTags?.tags,
                            insight = currentDayInsight?.insight,
                            metrics = currentDayMetrics,
                            comp = currentDayMeasurement
                        )
                    }
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice Input"
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            editingMeal = null
                            duplicatingMeal = null
                            initialDialogMealType = "Lunch"
                            initialDialogTime = null
                            initialDialogDesc = null
                            showAddDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Meal"
                        )
                    }
                }
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) { page ->
                val pageDate = LocalDate.now().plusDays((page - initialPage).toLong()).toString()
                val pageEntries by dao.getMealsForDate(pageDate).collectAsState(initial = emptyList())
                val pageTags by dao.getTagsForDate(pageDate).collectAsState(initial = null)
                val pageInsight by dao.getInsightForDate(pageDate).collectAsState(initial = null)
                val pageMetrics by dao.getMetricsForDate(pageDate).collectAsState(initial = null)
                val pageMeasurement by dao.getMeasurementForDate(pageDate).collectAsState(initial = null)

                val prevDayDate = LocalDate.parse(pageDate).minusDays(1).toString()
                val prevDayEntries by dao.getMealsForDate(prevDayDate).collectAsState(initial = emptyList())
                val prevLastMeal = prevDayEntries.maxByOrNull { it.time }
                val currFirstMeal = pageEntries.minByOrNull { it.time }

                var fastingDuration by remember(prevLastMeal, currFirstMeal) {
                    mutableStateOf("--")
                }

                LaunchedEffect(prevLastMeal, currFirstMeal) {
                    if (prevLastMeal != null && currFirstMeal != null) {
                        try {
                            val dt1 = LocalDateTime.of(
                                LocalDate.parse(prevDayDate),
                                LocalTime.parse(prevLastMeal.time, DateTimeFormatter.ofPattern("HH:mm"))
                            )
                            val dt2 = LocalDateTime.of(
                                LocalDate.parse(pageDate),
                                LocalTime.parse(currFirstMeal.time, DateTimeFormatter.ofPattern("HH:mm"))
                            )
                            val mins = ChronoUnit.MINUTES.between(dt1, dt2)
                            if (mins > 0) {
                                fastingDuration = "${mins / 60}h ${mins % 60}m"
                            } else {
                                fastingDuration = "--"
                            }
                        } catch (e: Exception) {
                            fastingDuration = "--"
                        }
                    } else {
                        fastingDuration = "--"
                    }
                }

                val activeTagsList = pageTags?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val visualTags = activeTagsList.filter { !it.startsWith("Friction:") && !it.startsWith("Sleep:") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    focusManager.clearFocus()
                                }
                            )
                        }
                ) {
                    if (availableTags.isNotEmpty() && pageEntries.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableTags.filter { it.isNotBlank() }.sorted().forEach { tag ->
                                val isSelected = visualTags.contains(tag)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val currentFrictionTag = activeTagsList.find { it.startsWith("Friction:") }
                                            val currentSleepTag = activeTagsList.find { it.startsWith("Sleep:") }

                                            val cleaned = activeTagsList.filter { !it.startsWith("Friction:") && !it.startsWith("Sleep:") }
                                            val newVisuals = if (isSelected) cleaned.filter { it != tag } else cleaned + tag

                                            val buildTags = mutableListOf<String>().apply {
                                                addAll(newVisuals)
                                                currentFrictionTag?.let { add(it) }
                                                currentSleepTag?.let { add(it) }
                                            }

                                            dao.insertTags(
                                                DailyTagEntity(
                                                    date = pageDate,
                                                    tags = buildTags.joinToString(",")
                                                )
                                            )
                                            MacroWidget().updateAll(context)
                                        }
                                    },
                                    label = {
                                        Text(text = tag)
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {

                        if (pageEntries.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .offset(y = (-32).dp),
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WbSunny,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(
                                        modifier = Modifier.height(16.dp)
                                    )

                                    Text(
                                        text = "Morning Intent",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "Establish your baseline for today.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(
                                        modifier = Modifier.height(32.dp)
                                    )

                                    // --- COGNITIVE LOAD ROW ---
                                    Text(
                                        text = "Cognitive Load",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Start)
                                    )

                                    Spacer(
                                        modifier = Modifier.height(8.dp)
                                    )

                                    val currentFriction = activeTagsList.find { it.startsWith("Friction:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        (1..5).forEach { level ->
                                            FilterChip(
                                                selected = currentFriction == level,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val cleaned = activeTagsList.filter { !it.startsWith("Friction:") }
                                                        val newTags = if (currentFriction == level) cleaned else cleaned + "Friction: $level"
                                                        dao.insertTags(
                                                            DailyTagEntity(
                                                                date = pageDate,
                                                                tags = newTags.joinToString(",")
                                                            )
                                                        )
                                                        MacroWidget().updateAll(context)
                                                    }
                                                },
                                                label = {
                                                    Text(text = level.toString())
                                                },
                                                shape = CircleShape
                                            )
                                        }
                                    }

                                    Spacer(
                                        modifier = Modifier.height(24.dp)
                                    )

                                    // --- V4.6 SLEEP SCORE ROW ---
                                    Text(
                                        text = "Sleep Quality",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Start)
                                    )

                                    Spacer(
                                        modifier = Modifier.height(8.dp)
                                    )

                                    val currentSleep = activeTagsList.find { it.startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        (1..5).forEach { level ->
                                            FilterChip(
                                                selected = currentSleep == level,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val cleaned = activeTagsList.filter { !it.startsWith("Sleep:") }
                                                        val newTags = if (currentSleep == level) cleaned else cleaned + "Sleep: $level"
                                                        dao.insertTags(
                                                            DailyTagEntity(
                                                                date = pageDate,
                                                                tags = newTags.joinToString(",")
                                                            )
                                                        )
                                                        MacroWidget().updateAll(context)
                                                    }
                                                },
                                                label = {
                                                    Text(text = level.toString())
                                                },
                                                shape = CircleShape
                                            )
                                        }
                                    }

                                    Spacer(
                                        modifier = Modifier.height(24.dp)
                                    )

                                    // --- CONTEXT TAGS ROW ---
                                    Text(
                                        text = "Context Tags",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Start)
                                    )

                                    Spacer(
                                        modifier = Modifier.height(8.dp)
                                    )

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        availableTags.filter { it.isNotBlank() }.sorted().forEach { tag ->
                                            val isSelected = visualTags.contains(tag)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val currentFrictionTag = activeTagsList.find { it.startsWith("Friction:") }
                                                        val currentSleepTag = activeTagsList.find { it.startsWith("Sleep:") }

                                                        val cleaned = activeTagsList.filter { !it.startsWith("Friction:") && !it.startsWith("Sleep:") }
                                                        val newVisuals = if (isSelected) cleaned.filter { it != tag } else cleaned + tag

                                                        val buildTags = mutableListOf<String>().apply {
                                                            addAll(newVisuals)
                                                            currentFrictionTag?.let { add(it) }
                                                            currentSleepTag?.let { add(it) }
                                                        }

                                                        dao.insertTags(
                                                            DailyTagEntity(
                                                                date = pageDate,
                                                                tags = buildTags.joinToString(",")
                                                            )
                                                        )
                                                        MacroWidget().updateAll(context)
                                                    }
                                                },
                                                label = {
                                                    Text(text = tag)
                                                },
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(280.dp)
                                    .alpha(0.15f)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 100.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = pageEntries,
                                    key = { _, item -> item.id }
                                ) { index, entry ->
                                    Box(
                                        modifier = Modifier
                                            .animateItem()
                                            .offset(y = (40.dp * (1f - entrance.value) * (index + 1)))
                                            .alpha(entrance.value)
                                    ) {
                                        MealCard(
                                            entry = entry,
                                            themePreference = themePreference,
                                            pitch = gyroPitch,
                                            roll = gyroRoll,
                                            onClick = {
                                                editingMeal = entry
                                                duplicatingMeal = null
                                                showAddDialog = true
                                            },
                                            onDuplicate = {
                                                duplicatingMeal = entry
                                                editingMeal = null
                                                showAddDialog = true
                                            },
                                            onDelete = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                coroutineScope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        dao.deleteMeal(entry)
                                                        MacroWidget().updateAll(context)
                                                    }

                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "${entry.type} deleted",
                                                        actionLabel = "UNDO",
                                                        duration = SnackbarDuration.Short
                                                    )

                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        withContext(Dispatchers.IO) {
                                                            dao.insertMeal(entry)
                                                            MacroWidget().updateAll(context)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                item {
                                    var insightText by remember(pageInsight?.insight) { mutableStateOf(pageInsight?.insight ?: "") }
                                    var totalKcal by remember(pageMetrics?.totalKcal) { mutableStateOf(pageMetrics?.totalKcal ?: "") }
                                    var deficit by remember(pageMetrics?.deficit) { mutableStateOf(pageMetrics?.deficit ?: "") }
                                    var inputWeight by remember(pageMeasurement?.weight) { mutableStateOf(pageMeasurement?.weight ?: "") }
                                    var inputFat by remember(pageMeasurement?.bodyFat) { mutableStateOf(pageMeasurement?.bodyFat ?: "") }
                                    var isCardFocused by remember { mutableStateOf(false) }
                                    var showFastTooltip by remember { mutableStateOf(false) }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(20.dp)
                                                .onFocusChanged { isCardFocused = it.hasFocus }
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
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(
                                                        modifier = Modifier.width(8.dp)
                                                    )
                                                    Text(
                                                        text = "Daily Summary & AI",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        showFastTooltip = !showFastTooltip
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Timer,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(
                                                        modifier = Modifier.width(4.dp)
                                                    )
                                                    Text(
                                                        text = "Fast: $fastingDuration",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }

                                            AnimatedVisibility(visible = showFastTooltip) {
                                                Text(
                                                    text = "Calculation: Measures the time elapsed between your final meal logged yesterday and your first meal logged today.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(top = 8.dp)
                                                )
                                            }

                                            Spacer(
                                                modifier = Modifier.height(12.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = totalKcal,
                                                    onValueChange = {
                                                        totalKcal = it
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            dao.insertMetric(
                                                                DailyMetricEntity(
                                                                    date = pageDate,
                                                                    totalKcal = it,
                                                                    deficit = deficit
                                                                )
                                                            )
                                                            MacroWidget().updateAll(context)
                                                        }
                                                    },
                                                    label = {
                                                        Text(text = "Total Kcal")
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                        focusedBorderColor = MaterialTheme.colorScheme.secondary
                                                    )
                                                )

                                                OutlinedTextField(
                                                    value = deficit,
                                                    onValueChange = {
                                                        deficit = it
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            dao.insertMetric(
                                                                DailyMetricEntity(
                                                                    date = pageDate,
                                                                    totalKcal = totalKcal,
                                                                    deficit = it
                                                                )
                                                            )
                                                            MacroWidget().updateAll(context)
                                                        }
                                                    },
                                                    label = {
                                                        Text(text = "Deficit")
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                        focusedBorderColor = MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            }

                                            Spacer(
                                                modifier = Modifier.height(12.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = inputWeight,
                                                    onValueChange = {
                                                        inputWeight = it
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            dao.insertMeasurement(
                                                                MeasurementEntity(
                                                                    date = pageDate,
                                                                    weight = it,
                                                                    bodyFat = inputFat
                                                                )
                                                            )
                                                        }
                                                    },
                                                    label = {
                                                        Text(text = "Weight (kg)")
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                        focusedBorderColor = MaterialTheme.colorScheme.secondary
                                                    )
                                                )

                                                OutlinedTextField(
                                                    value = inputFat,
                                                    onValueChange = {
                                                        inputFat = it
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            dao.insertMeasurement(
                                                                MeasurementEntity(
                                                                    date = pageDate,
                                                                    weight = inputWeight,
                                                                    bodyFat = it
                                                                )
                                                            )
                                                        }
                                                    },
                                                    label = {
                                                        Text(text = "Body Fat (%)")
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                                        focusedBorderColor = MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            }

                                            Spacer(
                                                modifier = Modifier.height(12.dp)
                                            )

                                            OutlinedTextField(
                                                value = insightText,
                                                onValueChange = {
                                                    insightText = it
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        dao.insertInsight(
                                                            DailyInsightEntity(
                                                                date = pageDate,
                                                                insight = it
                                                            )
                                                        )
                                                    }
                                                },
                                                placeholder = {
                                                    Text(
                                                        text = "Paste AI conclusions here...",
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = MaterialTheme.typography.bodyLarge,
                                                keyboardOptions = KeyboardOptions(
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { focusManager.clearFocus() }
                                                ),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                                                )
                                            )

                                            if (isCardFocused) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            focusManager.clearFocus()
                                                        },
                                                        contentPadding = PaddingValues(
                                                            horizontal = 12.dp,
                                                            vertical = 4.dp
                                                        )
                                                    ) {
                                                        Text(
                                                            text = "OK",
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            fontWeight = FontWeight.Bold
                                                        )
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
            }
        }

        AnimatedVisibility(
            visible = showAddDialog,
            enter = scaleIn(
                initialScale = 0.05f,
                transformOrigin = TransformOrigin(0.9f, 0.9f),
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(400)),
            exit = scaleOut(
                targetScale = 0.05f,
                transformOrigin = TransformOrigin(0.9f, 0.9f),
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showAddDialog = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures {}
                    }
                ) {
                    AddMealDialog(
                        existingMeal = editingMeal ?: duplicatingMeal,
                        initialType = initialDialogMealType,
                        initialTimeOverride = initialDialogTime,
                        initialDesc = initialDialogDesc,
                        isDuplicating = duplicatingMeal != null,
                        bannedSuggestions = bannedSuggestions,
                        onBanSuggestion = { bannedText ->
                            coroutineScope.launch {
                                context.dataStore.edit { prefs ->
                                    val current = prefs[BANNED_SUGGESTIONS_KEY] ?: emptySet()
                                    val updatedSet = HashSet(current)
                                    updatedSet.add(bannedText)
                                    prefs[BANNED_SUGGESTIONS_KEY] = updatedSet
                                }
                            }
                        },
                        dao = dao,
                        onDismiss = { showAddDialog = false },
                        onSave = { timeNow, selectedType, text ->
                            coroutineScope.launch(Dispatchers.IO) {
                                if (editingMeal != null) {
                                    dao.updateMeal(
                                        editingMeal!!.copy(
                                            time = timeNow,
                                            type = selectedType,
                                            description = text
                                        )
                                    )
                                } else {
                                    dao.insertMeal(
                                        MealEntity(
                                            id = UUID.randomUUID().toString(),
                                            date = currentDate.toString(),
                                            time = timeNow,
                                            type = selectedType,
                                            description = text
                                        )
                                    )
                                }
                                MacroWidget().updateAll(context)
                            }
                            showAddDialog = false
                        }
                    )
                }
            }
        }
    }
}

fun copyToClipboard(
    context: Context,
    entries: List<MealEntity>,
    date: LocalDate,
    tags: String?,
    insight: String?,
    metrics: DailyMetricEntity?,
    comp: MeasurementEntity?
) {
    if (entries.isEmpty()) {
        Toast.makeText(context, "Nothing to copy!", Toast.LENGTH_SHORT).show()
        return
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val textToCopy = StringBuilder()

    textToCopy.append("**${date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy"))}**\n\n")

    if (!tags.isNullOrBlank()) {
        textToCopy.append("**Context Tags:** $tags\n\n")
    }

    if (comp != null && (comp.weight.isNotBlank() || comp.bodyFat.isNotBlank())) {
        textToCopy.append("**Body Comp:** ${comp.weight}kg | ${comp.bodyFat}%\n")
    }

    if (metrics != null && (metrics.totalKcal.isNotBlank() || metrics.deficit.isNotBlank())) {
        textToCopy.append("**Macros:** Kcal: ${metrics.totalKcal} | Deficit: ${metrics.deficit}\n")
    }

    if ((comp != null && (comp.weight.isNotBlank() || comp.bodyFat.isNotBlank())) ||
        (metrics != null && (metrics.totalKcal.isNotBlank() || metrics.deficit.isNotBlank()))) {
        textToCopy.append("\n")
    }

    if (!insight.isNullOrBlank()) {
        textToCopy.append("**AI Insight:**\n$insight\n\n")
    }

    textToCopy.append("| Time | Type | Description |\n| :--- | :--- | :--- |\n")
    entries.forEach {
        textToCopy.append("| ${it.time} | ${it.type} | ${it.description.replace("|", "")} |\n")
    }

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "Food Log",
            textToCopy.toString()
        )
    )
    Toast.makeText(context, "Copied for Analysis", Toast.LENGTH_SHORT).show()
}