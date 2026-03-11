package com.example.gryzorfoodtracker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

val AUTO_BACKUP_URI_KEY = stringPreferencesKey("auto_backup_uri")
val FASTING_TARGET_KEY = stringPreferencesKey("fasting_target") // V4.8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    db: AppDatabase
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val dao = db.mealDao()

    val themePreference by context.dataStore.data
        .map { it[THEME_MODE_KEY] ?: "system" }
        .collectAsState("system")

    val phasePreference by context.dataStore.data
        .map { it[PHASE_MODE_KEY] ?: "cut" }
        .collectAsState("cut")

    val targetWeightStr by context.dataStore.data
        .map { it[TARGET_WEIGHT_KEY] ?: "" }
        .collectAsState("")

    val fastingTargetStr by context.dataStore.data
        .map { it[FASTING_TARGET_KEY] ?: "" }
        .collectAsState("")

    val customTags by context.dataStore.data
        .map { it[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS }
        .collectAsState(DEFAULT_TAGS)

    val autoBackupUriStr by context.dataStore.data
        .map { it[AUTO_BACKUP_URI_KEY] ?: "" }
        .collectAsState("")

    var newTagText by remember { mutableStateOf("") }
    var requiresRestart by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessVeryLow
            )
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)", null)
                    cursor.moveToFirst()
                    cursor.close()

                    val currentDbFile = context.getDatabasePath("food_tracker_db")
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        currentDbFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Database Exported", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val currentDbFile = context.getDatabasePath("food_tracker_db")
                    val walFile = context.getDatabasePath("food_tracker_db-wal")
                    val shmFile = context.getDatabasePath("food_tracker_db-shm")

                    db.close()

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        currentDbFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()

                    withContext(Dispatchers.Main) { requiresRestart = true }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                context.dataStore.edit { it[AUTO_BACKUP_URI_KEY] = uri.toString() }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Auto-Backup Folder Set!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (requiresRestart) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text(text = "Import Successful") },
            text = { Text(text = "The database has been restored. The app must restart to apply the changes safely.") },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val intent = Intent(context, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                ) { Text(text = "Restart App") }
            }
        )
    }

    if (showManual) {
        AlertDialog(
            onDismissRequest = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showManual = false
            },
            title = { Text(text = "App Manual") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = "The Capture Engine",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Voice Input: Hold the Mic button, say 'Snack, an apple at 4 pm', and let the AI parse the rest.\n• Gestures: Swipe a meal left to delete. Swipe right to instantly duplicate it.\n• Long-Press: Hold down a suggested meal chip to banish typos from your history.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "The Context Layer",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Tags: Use the chip bar to flag daily conditions.\n• Morning Intent: Empty days display a dashboard to set your daily Cognitive Load and Sleep Score before logging food.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Behavioral Engine",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Burnout Meter: Predicts system fatigue based on deficit streaks, metabolic volatility, and compromised sleep.\n• Intake VIX: Tracks intake volatility to prevent erratic eating patterns.\n• Fuel ROI: Measures if surpluses are efficiently fueling 'Grind' days.\n• Willpower Tax: Correlates your Sleep Quality against your deficit success rate.\n• Velocity Burn-Down: Forecasts when you will hit your Target Weight based on 31-day momentum.\n• Recovery Debt Ratio: Monitors CNS fatigue by tracking the ratio of 'Grind' vs 'Rest/Recovery' tags.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showManual = false
                    }
                ) { Text(text = "Close") }
            }
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showChangelog = false
            },
            title = { Text(text = "Version History") },
            text = {
                LazyColumn {
                    val logs = listOf(
                        "v4.8" to "The Tactile & Context Pass: Added continuous haptic data scrubbing to canvases with Relative Tooltips (Deltas). Added optional Fasting Target gamification to the Daily UI.",
                        "v4.7" to "The Signal vs. Noise Pass: Upgraded Analytics to 31-day horizons with a Trailing 7-Day Average signal overlay. Renamed Total Kcal to Total Intake. Added Weekly P&L and Success Blueprint to the Behavioral Engine. Built OTA GitHub updater with Auto-Backup directory linking.",
                        "v4.6" to "The Recovery Pass: Deeply integrated subjective Sleep Scores into the Behavioral Engine. Upgraded Momentum to a Weighted Moving Average. Refined Burnout and Recovery Debt penalties.",
                        "v4.5" to "The Feedback Pass: Upgraded haptics to LongPress voltage. Refined Behavioral math. Added axis labels to Analytics canvases. Built the 'Morning Intent' dashboard.",
                        "v4.3" to "The Tactile Pass: Added ubiquitous UI haptic feedback. Transitioned default 'Fasting' tag to 'Recovery'. Built Self-Healing data matrix.",
                        "v4.2" to "The Human Performance Pass: Added Chrono-Biology Fasting Engine, Velocity Burn-Down Forecast, Recovery Debt Ratio.",
                        "v4.0" to "The Behavioral Pass: Introduced the Behavioral Engine with Predictive Degradation, Intake VIX, Fuel ROI, Momentum Oscillator, and Ego Depletion Matrix.",
                        "v3.0" to "The Context Pass: Expanded application beyond simple tracking. Introduced customizable Context Tags, dynamic Cognitive Load tracking, and Phase Modes (Cut/Bulk).",
                        "v2.0" to "The Capture Pass: Vastly reduced friction. Introduced the AI Voice Parsing engine, gesture-based entry duplication, and robust Room SQL database persistence.",
                        "v1.0" to "Initial Release: The baseline architecture. Simple daily Macro and Deficit tracking, manual meal entry, and fundamental timeline generation."
                    )
                    items(logs) { (version, notes) ->
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = version,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showChangelog = false
                    }
                ) { Text(text = "Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Options") },
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

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // --- APPEARANCE SECTION ---
            item {
                Column(modifier = elasticMod(0)) {
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 0.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val themeOptions = listOf(
                        Triple("system", "Follow System", Icons.Filled.SettingsBrightness),
                        Triple("dark", "Dark Mode (OLED)", Icons.Filled.DarkMode),
                        Triple("dim", "Dim Mode (Slate)", Icons.Filled.Nightlight),
                        Triple("light", "Standard Light", Icons.Filled.LightMode),
                        Triple("nordic", "Nordic Paper", Icons.Filled.MenuBook),
                        Triple("clinical", "Clinical Glass", Icons.Filled.MedicalServices),
                        Triple("monochrome", "Monochrome", Icons.Filled.Contrast)
                    )
                    themeOptions.forEach { (key, label, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        context.dataStore.edit { it[THEME_MODE_KEY] = key }
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themePreference == key,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // --- GOALS & PHASE SECTION ---
            item {
                Column(modifier = elasticMod(1)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 0.dp, end = 0.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CrisisAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Physical Goals",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = targetWeightStr,
                            onValueChange = { newVal ->
                                coroutineScope.launch {
                                    context.dataStore.edit { it[TARGET_WEIGHT_KEY] = newVal }
                                }
                            },
                            label = { Text(text = "Target Weight (kg)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        OutlinedTextField(
                            value = fastingTargetStr,
                            onValueChange = { newVal ->
                                coroutineScope.launch {
                                    context.dataStore.edit { it[FASTING_TARGET_KEY] = newVal }
                                }
                            },
                            label = { Text(text = "Fast Target (hrs)") },
                            placeholder = { Text(text = "Optional") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    context.dataStore.edit { it[PHASE_MODE_KEY] = "cut" }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = phasePreference == "cut",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Cut Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "A caloric deficit is considered a success.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    context.dataStore.edit { it[PHASE_MODE_KEY] = "bulk" }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = phasePreference == "bulk",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Bulk Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "A caloric surplus is considered a success.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // --- CONTEXT TAGS SECTION ---
            item {
                Column(modifier = elasticMod(2)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 0.dp, end = 0.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Context Tags",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTagText,
                            onValueChange = { newTagText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(text = "e.g. High Carb Day") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (newTagText.isNotBlank()) {
                                    coroutineScope.launch {
                                        context.dataStore.edit { prefs ->
                                            val updatedSet = HashSet(prefs[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS)
                                            updatedSet.add(newTagText.trim())
                                            prefs[CUSTOM_TAGS_KEY] = updatedSet
                                        }
                                    }
                                    newTagText = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(text = "Add")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(customTags.filter { it.isNotBlank() }.sorted()) { tag ->
                Row(
                    modifier = elasticMod(2)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                context.dataStore.edit { prefs ->
                                    val updatedSet = HashSet(prefs[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS)
                                    updatedSet.remove(tag)
                                    prefs[CUSTOM_TAGS_KEY] = updatedSet
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove Tag",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // --- DATA MANAGEMENT SECTION ---
            item {
                Column(modifier = elasticMod(3)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 0.dp, end = 0.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Data Management",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                folderPickerLauncher.launch(null)
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Set Auto-Backup Folder",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (autoBackupUriStr.isBlank()) "Not Set" else "Folder Linked",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoBackupUriStr.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                exportLauncher.launch("food_tracker_db_${LocalDate.now()}.db")
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Manual Export Database",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                importLauncher.launch(arrayOf("*/*"))
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Import Database",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch(Dispatchers.IO) {
                                    val random = Random(System.currentTimeMillis())
                                    var currentWeight = 82f
                                    val todayDate = LocalDate.now()

                                    (30 downTo 0).forEach { i ->
                                        val d = todayDate.minusDays(i.toLong()).toString()

                                        val isGrind = random.nextBoolean()
                                        val rSleep = random.nextInt(2, 6)
                                        val rLoad = random.nextInt(1, 5)
                                        val tags = mutableListOf("Friction: $rLoad", "Sleep: $rSleep")
                                        if (isGrind) tags.add("Grind") else tags.add("Recovery")

                                        dao.insertTags(DailyTagEntity(d, tags.joinToString(",")))

                                        val intake = 2000 + random.nextInt(800)
                                        val deficit = random.nextInt(300, 700) * if (random.nextFloat() > 0.2f) 1 else -1
                                        dao.insertMetric(DailyMetricEntity(d, intake.toString(), deficit.toString()))

                                        currentWeight -= (deficit / 7700f)
                                        dao.insertMeasurement(MeasurementEntity(d, String.format("%.1f", currentWeight), "15.5"))
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "31 Days of Seed Data Injected", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Inject Seed Data (Debug)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // --- ABOUT & SYSTEM SECTION ---
            item {
                Column(modifier = elasticMod(4)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showManual = true
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "App Manual",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showChangelog = true
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "View Changelog",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                uriHandler.openUri("https://github.com/exolon/GryzorFoodTracker")
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "GitHub Repository",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCheckingUpdate) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCheckingUpdate = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val url = URL("https://api.github.com/repos/exolon/GryzorFoodTracker/releases/latest")
                                        val connection = url.openConnection() as HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                            val response = connection.inputStream.bufferedReader().readText()
                                            val json = JSONObject(response)
                                            val latestTag = json.getString("tag_name")
                                            val assets = json.getJSONArray("assets")

                                            var apkUrl: String? = null
                                            for (i in 0 until assets.length()) {
                                                val asset = assets.getJSONObject(i)
                                                if (asset.getString("name").endsWith(".apk")) {
                                                    apkUrl = asset.getString("browser_download_url")
                                                    break
                                                }
                                            }

                                            val currentVersion = "v4.8"
                                            val latestVal = latestTag.replace("v", "").replace(".", "").toIntOrNull() ?: 0
                                            val currentVal = currentVersion.replace("v", "").replace(".", "").toIntOrNull() ?: 0

                                            if (latestVal > currentVal && apkUrl != null) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Version $latestTag found! Downloading...", Toast.LENGTH_LONG).show()
                                                }

                                                val request = DownloadManager.Request(Uri.parse(apkUrl))
                                                    .setTitle("Gryzor Update $latestTag")
                                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GryzorUpdate.apk")

                                                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                val downloadId = downloadManager.enqueue(request)

                                                val onComplete = object : BroadcastReceiver() {
                                                    override fun onReceive(ctxt: Context, intent: Intent) {
                                                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                                        if (id == downloadId) {
                                                            val currentReceiver = this

                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                val savedUriStr = context.dataStore.data.first()[AUTO_BACKUP_URI_KEY]
                                                                if (!savedUriStr.isNullOrBlank()) {
                                                                    try {
                                                                        val treeUri = Uri.parse(savedUriStr)
                                                                        val docId = DocumentsContract.getTreeDocumentId(treeUri)
                                                                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                                                        val backupUri = DocumentsContract.createDocument(
                                                                            context.contentResolver,
                                                                            docUri,
                                                                            "application/octet-stream",
                                                                            "gryzor_autobackup_${LocalDate.now()}_$currentVersion.db"
                                                                        )

                                                                        if (backupUri != null) {
                                                                            val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)", null)
                                                                            cursor.moveToFirst()
                                                                            cursor.close()

                                                                            val dbFile = context.getDatabasePath("food_tracker_db")
                                                                            context.contentResolver.openOutputStream(backupUri)?.use { outStream ->
                                                                                dbFile.inputStream().use { inStream ->
                                                                                    inStream.copyTo(outStream)
                                                                                }
                                                                            }
                                                                            withContext(Dispatchers.Main) {
                                                                                Toast.makeText(context, "Pre-update DB Backup secured.", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        withContext(Dispatchers.Main) {
                                                                            Toast.makeText(context, "Auto-Backup skipped/failed.", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }

                                                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                                    val fileUri = FileProvider.getUriForFile(
                                                                        ctxt,
                                                                        "${context.packageName}.provider",
                                                                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GryzorUpdate.apk")
                                                                    )
                                                                    setDataAndType(fileUri, "application/vnd.android.package-archive")
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(installIntent)
                                                                context.unregisterReceiver(currentReceiver)
                                                            }
                                                        }
                                                    }
                                                }

                                                context.registerReceiver(
                                                    onComplete,
                                                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                                                    Context.RECEIVER_EXPORTED
                                                )

                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Up to date ($currentVersion)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Failed to check for updates.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Error checking updates.", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) { isCheckingUpdate = false }
                                    }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isCheckingUpdate) "Checking GitHub..." else "Check for Updates",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, top = 24.dp, end = 0.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Gryzor Food Tracker",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "v4.8",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}