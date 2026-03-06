package com.example.gryzorfoodtracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal),
    Font(R.font.google_sans_flex, FontWeight.Medium),
    Font(R.font.google_sans_flex, FontWeight.SemiBold),
    Font(R.font.google_sans_flex, FontWeight.Bold)
)

val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = GoogleSansFlex),
    displayMedium = Typography().displayMedium.copy(fontFamily = GoogleSansFlex),
    displaySmall = Typography().displaySmall.copy(fontFamily = GoogleSansFlex),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = GoogleSansFlex),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = GoogleSansFlex),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = GoogleSansFlex),
    titleLarge = Typography().titleLarge.copy(fontFamily = GoogleSansFlex),
    titleMedium = Typography().titleMedium.copy(fontFamily = GoogleSansFlex),
    titleSmall = Typography().titleSmall.copy(fontFamily = GoogleSansFlex),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = GoogleSansFlex),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = GoogleSansFlex),
    bodySmall = Typography().bodySmall.copy(fontFamily = GoogleSansFlex),
    labelLarge = Typography().labelLarge.copy(fontFamily = GoogleSansFlex),
    labelMedium = Typography().labelMedium.copy(fontFamily = GoogleSansFlex),
    labelSmall = Typography().labelSmall.copy(fontFamily = GoogleSansFlex)
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

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
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
    scrollBehavior: TopAppBarScrollBehavior,
    onBehaviorClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Unit
) {
    LargeTopAppBar(
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (frictionScore >= 4) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { expanded = true }
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
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (frictionScore > 0) "Load: $frictionScore/5" else "Set Load",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (frictionScore >= 4) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            (1..5).forEach { level ->
                                DropdownMenuItem(
                                    text = { Text("Level $level Friction") },
                                    onClick = {
                                        onFrictionChange(level)
                                        expanded = false
                                    }
                                )
                            }
                            if (frictionScore > 0) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Clear", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        onFrictionChange(0)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (!dailyKcal.isNullOrBlank() || !dailyDeficit.isNullOrBlank() || !dailyWeight.isNullOrBlank() || !dailyFat.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))

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
            IconButton(onClick = onPrev) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = onBehaviorClick) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = "Behavioral Engine"
                )
            }
            IconButton(onClick = onAnalyticsClick) {
                Icon(
                    imageVector = Icons.Filled.Assessment,
                    contentDescription = "Analytics"
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share Markdown"
                )
            }
            IconButton(onClick = onSettingsClick) {
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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
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
                Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(24.dp))
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
                .clickable { onClick() }
                .graphicsLayer {
                    rotationX = (pitch * 0.15f).coerceIn(-8f, 8f)
                    rotationY = (roll * 0.15f).coerceIn(-8f, 8f)
                    cameraDistance = 12f * density
                },
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            border = BorderStroke(1.dp, borderColor)
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

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        fontWeight = FontWeight.Medium
                    )
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

    // Filter out suggestions that match the current text OR are on the banned list
    val filteredSuggestions = historicalSuggestions.filter {
        it.contains(mealText, ignoreCase = true) &&
                it != mealText &&
                !bannedSuggestions.contains(it)
    }

    if (suggestionToBan != null) {
        AlertDialog(
            onDismissRequest = { suggestionToBan = null },
            title = { Text("Remove Suggestion") },
            text = { Text("Hide '$suggestionToBan' from future suggestions?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBanSuggestion(suggestionToBan!!)
                        suggestionToBan = null
                    }
                ) {
                    Text("Hide", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { suggestionToBan = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (isDuplicating) "Duplicate Entry" else if (existingMeal != null) "Update Entry" else "New Entry",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                AssistChip(
                    onClick = { showTimePicker = true },
                    label = { Text("At $mealTime", style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mealTypes) { type ->
                        FilterChip(
                            selected = selectedMealType == type,
                            onClick = { selectedMealType = type },
                            label = { Text(type) },
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

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = mealText,
                    onValueChange = { mealText = it },
                    placeholder = { Text("What are we logging?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 3
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
                                        onClick = { mealText = suggestion },
                                        onLongClick = { suggestionToBan = suggestion }
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

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mealText.isNotBlank()) {
                                onSave(mealTime, selectedMealType, mealText)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Entry")
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        mealTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
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