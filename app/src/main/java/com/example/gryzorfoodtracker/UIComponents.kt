package com.example.gryzorfoodtracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val GoogleSansFlex = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Normal
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Medium
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.SemiBold
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Bold
    )
)

val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontFamily = GoogleSansFlex
    ),
    displayMedium = Typography().displayMedium.copy(
        fontFamily = GoogleSansFlex
    ),
    displaySmall = Typography().displaySmall.copy(
        fontFamily = GoogleSansFlex
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = GoogleSansFlex
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = GoogleSansFlex
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = GoogleSansFlex
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = GoogleSansFlex
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = GoogleSansFlex
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = GoogleSansFlex
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = GoogleSansFlex
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = GoogleSansFlex
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = GoogleSansFlex
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = GoogleSansFlex
    ),
    labelMedium = Typography().labelMedium.copy(
        fontFamily = GoogleSansFlex
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = GoogleSansFlex
    )
)

fun getMealIcon(type: String): ImageVector {
    return when (type) {
        "Breakfast" -> Icons.Filled.BakeryDining
        "Lunch" -> Icons.Filled.LunchDining
        "Dinner" -> Icons.Filled.DinnerDining
        "Snack" -> Icons.Filled.Fastfood
        else -> Icons.Filled.Restaurant
    }
}

@Composable
fun rememberGyroscopeTilt(): Pair<Float, Float> {
    val context = LocalContext.current
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return Pair(pitch, roll)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeDayHeader(
    date: LocalDate,
    dailyKcal: String?,
    dailyDeficit: String?,
    dailyWeight: String?,
    dailyFat: String?,
    frictionScore: Int,
    onFrictionChange: (Int) -> Unit,
    sleepScore: Int,
    onSleepChange: (Int) -> Unit,
    currentStreak: Int = 0,
    shieldCount: Int = 0,
    scrollBehavior: TopAppBarScrollBehavior,
    onBehaviorClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LargeTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var loadExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (frictionScore >= 4) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    loadExpanded = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (frictionScore >= 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(
                                        modifier = Modifier.width(4.dp)
                                    )
                                    Text(
                                        text = if (frictionScore > 0) "$frictionScore/5" else "Load",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (frictionScore >= 4) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = loadExpanded,
                                onDismissRequest = { loadExpanded = false }
                            ) {
                                (1..5).forEach { level ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = "Level $level Load")
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onFrictionChange(level)
                                            loadExpanded = false
                                        }
                                    )
                                }
                                if (frictionScore > 0) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Clear",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onFrictionChange(0)
                                            loadExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        var sleepExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sleepScore in 1..2) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sleepExpanded = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (sleepScore in 1..2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(
                                        modifier = Modifier.width(4.dp)
                                    )
                                    Text(
                                        text = if (sleepScore > 0) "$sleepScore/5" else "Sleep",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (sleepScore in 1..2) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = sleepExpanded,
                                onDismissRequest = { sleepExpanded = false }
                            ) {
                                (1..5).forEach { level ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = "Level $level Sleep")
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSleepChange(level)
                                            sleepExpanded = false
                                        }
                                    )
                                }
                                if (sleepScore > 0) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Clear",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSleepChange(0)
                                            sleepExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (currentStreak > 0 || shieldCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "$currentStreak",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            (1..3).forEach { index ->
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Shield",
                                    tint = if (index <= shieldCount) Color(0xFFF5B041) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (!dailyKcal.isNullOrBlank() || !dailyDeficit.isNullOrBlank() || !dailyWeight.isNullOrBlank() || !dailyFat.isNullOrBlank()) {
                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        val macros = listOf(
                            dailyKcal?.let { "$it Kcal" },
                            dailyDeficit?.let { "Def: $it" }
                        ).mapNotNull { it }.joinToString(" | ")

                        val comp = listOf(
                            dailyWeight?.let { "${it}kg" },
                            dailyFat?.let { "${it}%" }
                        ).mapNotNull { it }.joinToString(" | ")

                        Text(
                            text = listOf(macros, comp).filter { it.isNotEmpty() }.joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrev()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNext()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBehaviorClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = "Behavioral Engine"
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAnalyticsClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Assessment,
                    contentDescription = "Analytics"
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCopy()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share Markdown"
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSettingsClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Options"
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealCard(
    entry: MealEntity,
    themePreference: String,
    pitch: Float = 0f,
    roll: Float = 0f,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDuplicate()
                    false
                }
                else -> false
            }
        }
    )

    val isSystemDark = isSystemInDarkTheme()

    val cardColor = when (themePreference) {
        "dark" -> Color.White.copy(alpha = 0.07f)
        "dim" -> Color.White.copy(alpha = 0.04f)
        "light", "nordic", "clinical", "monochrome" -> MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        else -> if (isSystemDark) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    }

    val borderColor = when (themePreference) {
        "dark" -> Color.White.copy(alpha = 0.25f)
        "dim" -> Color.White.copy(alpha = 0.15f)
        "light", "nordic", "clinical", "monochrome" -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        else -> if (isSystemDark) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

    // --- V8.0 Parse hidden Trace Data ---
    val traceMatch = Regex("""""", RegexOption.DOT_MATCHES_ALL).find(entry.description)
    val traceData = traceMatch?.groupValues?.get(1)
    val cleanedOfTrace = entry.description.replace(Regex("""""", RegexOption.DOT_MATCHES_ALL), "").trim()

    val macroRegex = Regex("""\s*\[(.*?)\]$""")
    val match = macroRegex.find(cleanedOfTrace)
    val cleanDesc = if (match != null) cleanedOfTrace.replace(match.value, "").trim() else cleanedOfTrace
    val macrosStr = match?.groups?.get(1)?.value

    var showTraceDialog by remember { mutableStateOf(false) }

    if (showTraceDialog && traceData != null) {
        AlertDialog(
            onDismissRequest = { showTraceDialog = false },
            title = { Text("Gemini Engine Trace") },
            text = { Text(traceData, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showTraceDialog = false }) { Text("Close") } }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = color,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 24.dp)
            ) {
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .graphicsLayer {
                    rotationX = (pitch * 0.05f).coerceIn(-8f, 8f)
                    rotationY = (roll * 0.05f).coerceIn(-8f, 8f)
                    cameraDistance = 12f * density
                },
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            border = BorderStroke(
                width = 1.dp,
                color = borderColor
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getMealIcon(entry.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(16.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = cleanDesc,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 22.sp
                        ),
                        fontWeight = FontWeight.Medium
                    )
                    if (macrosStr != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = macrosStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // --- V8.0 Render AI Trace Button ---
                            if (traceData != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Troubleshoot,
                                    contentDescription = "View AI Trace",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp).clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTraceDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = entry.time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

suspend fun fetchMacros(context: Context, apiKey: String, description: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // V8.0: Dual-purpose prompt (Food + Workouts)
            val prompt = "Analyze this input: '$description'. If it is food, return ONLY a JSON object: {\"kcal\": 350, \"p\": 20, \"f\": 15, \"c\": 30}. If it is a physical workout/exercise, return ONLY a JSON object representing calories burned as a negative number, with macros as 0: {\"kcal\": -400, \"p\": 0, \"f\": 0, \"c\": 0}. Do not use markdown."

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                val textResult = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val cleanJsonString = textResult.replace("```json", "").replace("```", "").trim()
                val macroJson = JSONObject(cleanJsonString)

                val kcal = macroJson.optInt("kcal", 0)
                val p = macroJson.optInt("p", 0)
                val f = macroJson.optInt("f", 0)
                val c = macroJson.optInt("c", 0)

                // Package the raw trace silently into the string
                val traceData = ""
                " [$kcal kcal | ${p}g P | ${f}g F | ${c}g C] $traceData"
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown API Error"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "API Error ${connection.responseCode}: Check Key", Toast.LENGTH_LONG).show()
                }
                ""
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "JSON Parse/Network Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            ""
        }
    }
}

suspend fun fetchSalvageIdea(context: Context, apiKey: String, targetDeficit: String, phase: String, recentMeals: List<MealEntity> = emptyList()): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val mealContext = if (recentMeals.isNotEmpty()) {
                "Base your suggestion heavily on my palate. I usually eat things like: " + recentMeals.joinToString(", ") { it.description.substringBefore("[").trim() }
            } else {
                ""
            }

            val prompt = if (phase == "cut") {
                "I am cutting and struggling. My current deficit logged is $targetDeficit. $mealContext. Give me exactly ONE short, hyper-specific, extremely high-satiety, low-calorie rescue meal idea to stop me from bingeing further today. Max 2 sentences. No markdown. No intro/outro text."
            } else {
                "I am bulking and missing my target. My current surplus logged is $targetDeficit. $mealContext. Give me exactly ONE short, hyper-specific, highly calorie-dense, low-volume meal idea to easily hit my surplus today. Max 2 sentences. No markdown. No intro/outro text."
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                val textResult = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                textResult.replace("```", "").trim()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Salvage API Error ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                }
                ""
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Network Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            ""
        }
    }
}

// --- V8.0 WEEKLY EXECUTIVE BRIEF ENGINE ---
suspend fun fetchWeeklyBrief(context: Context, apiKey: String, phase: String, weeklyDataStr: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("[https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$](https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$){apiKey.trim()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val prompt = """
                You are a behavioral scientist and high-performance coach. 
                Analyze the following 7 days of user data. The user is currently in a "$phase" phase.
                Data format: Date | Intake | Deficit | Context Tags (includes Friction 1-5 and Sleep 1-5).
                
                $weeklyDataStr
                
                Identify blind spots and correlations (e.g., "Your compliance drops significantly when Sleep is Level 2").
                Provide EXACTLY 3 short, punchy, actionable bullet points. 
                Do not use corporate jargon. Use plain English. No introductory text, no fluff.
            """.trimIndent()

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                val textResult = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                textResult.replace("```", "").trim()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Briefing API Error ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                }
                ""
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Network Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            ""
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddMealDialog(
    existingMeal: MealEntity?,
    initialType: String?,
    initialTimeOverride: String?,
    initialDesc: String?,
    isDuplicating: Boolean,
    bannedSuggestions: Set<String>,
    onBanSuggestion: (String) -> Unit,
    dao: MealDao,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var backProgress by remember { mutableFloatStateOf(0f) }
    var isCalculating by remember { mutableStateOf(false) }

    val geminiApiStr by context.dataStore.data
        .map { it[stringPreferencesKey("gemini_api_key")] ?: "" }
        .collectAsState("")

    var mealText by remember { mutableStateOf(initialDesc ?: existingMeal?.description ?: "") }
    var selectedMealType by remember { mutableStateOf(initialType ?: existingMeal?.type ?: "Lunch") }

    val defaultTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    val resolvedTime = initialTimeOverride ?: if (existingMeal != null && !isDuplicating) existingMeal.time else defaultTime
    var mealTime by remember { mutableStateOf(resolvedTime) }

    val mealTypes = listOf("Breakfast", "Lunch", "Dinner", "Snack")

    var showTimePicker by remember { mutableStateOf(false) }
    var suggestionToBan by remember { mutableStateOf<String?>(null) }

    val timePickerState = rememberTimePickerState(
        initialHour = mealTime.split(":")[0].toIntOrNull() ?: LocalTime.now().hour,
        initialMinute = mealTime.split(":")[1].toIntOrNull() ?: LocalTime.now().minute,
        is24Hour = true
    )

    val historicalSuggestions by dao.getSuggestions(selectedMealType).collectAsState(initial = emptyList())

    // Strip traces from historical suggestions so they don't pollute the UI picker
    val filteredSuggestions = historicalSuggestions.map { it.replace(Regex("""""", RegexOption.DOT_MATCHES_ALL), "").trim() }.distinct().filter {
        it.contains(mealText, ignoreCase = true) &&
                it != mealText &&
                !bannedSuggestions.contains(it)
    }

    PredictiveBackHandler(enabled = !isCalculating) { progressStream ->
        try {
            progressStream.collect { backEvent ->
                backProgress = backEvent.progress
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            keyboardController?.hide()
            onDismiss()
        } catch (e: CancellationException) {
            backProgress = 0f
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {}
    }

    if (suggestionToBan != null) {
        AlertDialog(
            onDismissRequest = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                suggestionToBan = null
            },
            title = {
                Text(text = "Remove Suggestion")
            },
            text = {
                Text(text = "Hide '$suggestionToBan' from future suggestions?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBanSuggestion(suggestionToBan!!)
                        suggestionToBan = null
                    }
                ) {
                    Text(
                        text = "Hide",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        suggestionToBan = null
                    }
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    val executeSave = {
        if (mealText.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            keyboardController?.hide()

            if (geminiApiStr.isNotBlank() && !mealText.contains("kcal |")) {
                isCalculating = true
                coroutineScope.launch {
                    val macroAppend = fetchMacros(context, geminiApiStr, mealText)
                    isCalculating = false
                    onSave(mealTime, selectedMealType, (mealText + macroAppend).trim())
                }
            } else {
                onSave(mealTime, selectedMealType, mealText.trim())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.5f * (1f - backProgress)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isCalculating) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    keyboardController?.hide()
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 400.dp)
                .graphicsLayer {
                    val scale = 1f - (backProgress * 0.15f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (backProgress * 0.5f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = if (isDuplicating) "Duplicate Entry" else if (existingMeal != null) "Update Entry" else "New Entry",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                AssistChip(
                    onClick = {
                        if (!isCalculating) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showTimePicker = true
                        }
                    },
                    label = {
                        Text(
                            text = "At $mealTime",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mealTypes) { type ->
                        FilterChip(
                            selected = selectedMealType == type,
                            onClick = {
                                if (!isCalculating) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedMealType = type
                                }
                            },
                            label = {
                                Text(text = type)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = getMealIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = mealText,
                    onValueChange = {
                        if (!isCalculating) mealText = it
                    },
                    placeholder = {
                        Text(text = "What are we logging?")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 3,
                    enabled = !isCalculating,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    )
                )

                if (filteredSuggestions.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSuggestions) { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        enabled = !isCalculating,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            mealText = suggestion
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            suggestionToBan = suggestion
                                        }
                                    )
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCalculating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                keyboardController?.hide()
                                onDismiss()
                            }
                        ) {
                            Text(text = "Cancel")
                        }
                    }

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Button(
                        onClick = executeSave,
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCalculating
                    ) {
                        Text(
                            text = if (isCalculating) "Calculating..." else "Save Entry",
                            modifier = Modifier.defaultMinSize(minWidth = 140.dp)
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker && !isCalculating) {
        DatePickerDialog(
            onDismissRequest = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showTimePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        mealTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text(text = "OK")
                }
            }
        ) {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}