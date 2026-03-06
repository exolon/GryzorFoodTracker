package com.example.gryzorfoodtracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, db: AppDatabase) {
    val context = LocalContext.current
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

    val customTags by context.dataStore.data
        .map { it[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS }
        .collectAsState(DEFAULT_TAGS)

    var newTagText by remember { mutableStateOf("") }
    var requiresRestart by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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

    if (requiresRestart) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Import Successful") },
            text = { Text("The database has been restored. The app must restart to apply the changes safely.") },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                ) {
                    Text("Restart App")
                }
            }
        )
    }

    if (showManual) {
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("App Manual") },
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

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "The Context Layer",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Morning Intent: A daily modal prompting you to pre-load your physical strategy and cognitive load upon waking.\n• Tags: Use the chip bar to flag daily conditions (e.g., Grind, Fasting).\n• Cognitive Load: Tap the 'Load' dropdown next to the date to log the daily stress/friction (1-5).",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Behavioral Engine",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Circuit Breaker: Automatically recommends tactical maintenance if Cognitive Load is logged at a 4 or 5.\n• Burnout Meter: Predicts system fatigue based on deficit streaks and scale.\n• Caloric VIX: Tracks intake volatility to prevent erratic eating patterns.\n• Fuel ROI: Measures if surpluses are efficiently fueling 'Grind' days.\n• Ego Depletion Matrix: Correlates your Cognitive Load inputs against your deficit success.\n• Velocity Burn-Down: Forecasts when you will hit your Target Weight based on 14-day momentum.\n• Recovery Debt Ratio: Monitors CNS fatigue by tracking the ratio of 'Grind' to 'Rest' tags.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManual = false }) { Text("Close") }
            }
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("Version History") },
            text = {
                LazyColumn {
                    val logs = listOf(
                        // --- V4.3 CHANGELOG ADDITION ---
                        "v4.3" to "The Intent Pass: Added the Morning Intent Protocol to pre-load daily strategy, and the Cognitive Circuit Breaker to automatically recommend tactical maintenance on high-stress days.",
                        "v4.2" to "The Human Performance Pass: Added Chrono-Biology Fasting Engine, Velocity Burn-Down Forecast, Recovery Debt Ratio, and Long-Press Suggestion Banishment.",
                        "v4.1" to "The UX Polish Pass: Added explicit Help Tooltips to all charts and behavioral metrics. Isolated 'Cognitive Load' into a dedicated Dropdown Header Picker.",
                        "v4.0" to "The Behavioral Pass: Introduced the Behavioral Engine with Predictive Degradation (Burnout Meter), Caloric VIX (Metabolic Volatility), Marginal Fuel ROI, Momentum Oscillator, and Ego Depletion Matrix."
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
                TextButton(onClick = { showChangelog = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Options") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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
                        Spacer(Modifier.width(12.dp))
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
                                    coroutineScope.launch {
                                        context.dataStore.edit { it[THEME_MODE_KEY] = key }
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themePreference == key, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(16.dp))
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
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Physical Goals",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = targetWeightStr,
                        onValueChange = { newVal ->
                            coroutineScope.launch {
                                context.dataStore.edit { it[TARGET_WEIGHT_KEY] = newVal }
                            }
                        },
                        label = { Text("Target Body Weight (kg)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    context.dataStore.edit { it[PHASE_MODE_KEY] = "cut" }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = phasePreference == "cut", onClick = null)
                        Spacer(Modifier.width(16.dp))
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
                                coroutineScope.launch {
                                    context.dataStore.edit { it[PHASE_MODE_KEY] = "bulk" }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = phasePreference == "bulk", onClick = null)
                        Spacer(Modifier.width(16.dp))
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
                        Spacer(Modifier.width(12.dp))
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
                            placeholder = { Text("e.g. High Carb Day") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
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
                        ) { Text("Add") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            items(customTags.filter { it.isNotBlank() }.sorted()) { tag ->
                Row(
                    modifier = elasticMod(2)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tag, style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        onClick = {
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
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Data Management",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportLauncher.launch("food_tracker_db_${LocalDate.now()}.db") }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Export Database",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importLauncher.launch(arrayOf("*/*")) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Import Database",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // --- ABOUT SECTION ---
            item {
                Column(modifier = elasticMod(4)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManual = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "App Manual",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangelog = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "View Changelog",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // --- APP VERSION FOOTER ---
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
                    // --- V4.3 BUMP ---
                    Text(
                        text = "v4.3",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}