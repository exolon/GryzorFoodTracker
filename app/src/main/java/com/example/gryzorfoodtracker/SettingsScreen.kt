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
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Database Exported", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show() }
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
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Import Failed", Toast.LENGTH_SHORT).show() }
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
                Button(onClick = {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }) { Text("Restart App") }
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
                        Text("The Capture Engine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Voice Input: Hold the Mic button, say 'Snack, an apple at 4 pm', and let the AI parse the rest.\n• Gestures: Swipe a meal left to delete. Swipe right to instantly duplicate it.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("The Context Layer", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Tags: Use the chip bar to flag daily conditions (e.g., Grind, Fasting).", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("The AI Loop", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Weekly Strategic Review: Export your trailing 7 days (including your weight/fat inputs) to your AI for high-level pattern recognition.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("Analytics Dashboard", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Features: View your 14-day Macro Trend, Body Comp Trend, Trajectory Engine, and Behavioral Compliance Matrix.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("Behavioral Engine (v4.0)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Burnout Meter: Predicts system fatigue based on deficit streaks and scale.\n• Caloric VIX: Tracks intake volatility to prevent erratic eating patterns.\n• Fuel ROI: Measures if surpluses are efficiently fueling 'Grind' days.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManual = false }) { Text("Close") } }
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("Version History") },
            text = {
                LazyColumn {
                    val logs = listOf(
                        "v4.0" to "The Behavioral Pass: Introduced the Behavioral Engine with Predictive Degradation (Burnout Meter), Caloric VIX (Metabolic Volatility), and Marginal Fuel ROI.",
                        "v3.9" to "The Reporting Pass: Enhanced native PDF export with scaled vector line-graphs for Macros and Body Composition. Fully mapped Historical Version Log.",
                        "v3.8" to "The Executive Pass: Added Dietary Phase toggling (Cut vs Bulk), Heatmap navigation, Haptic Chart Scrubbing, and single-page PDF generation.",
                        "v3.7" to "The Polish Pass: Restored core UI stability, fixed content clipping under App Bars, bound Heatmap to 30-day wrap logic.",
                        "v3.6" to "The Bright Pass: Added 3 Light Themes, dialed down Parallax physics, and restored Heatmap.",
                        "v3.5" to "The Bleeding Edge Pass: Hardware Gyroscope Parallax, Kinetic Typography font morphing, and transparent Glass headers.",
                        "v3.4" to "The Aura Update: Added Apple-Health style gradient engine, number tickers, and fluid container morphs.",
                        "v3.3" to "The Kinesthetic Pass: Animated canvas charting and Haptic Feedback engine integration.",
                        "v3.2" to "Architecture Pass: Finalized decoupled structure with dedicated Home, Analytics, and Settings environments.",
                        "v3.1" to "The Storage Pass: Transitioned away from Datastore and introduced Room Database for persistent relational storage.",
                        "v3.0" to "The Framework Pass: Full migration from XML to Jetpack Compose.",
                        "v2.0" to "The Insight Pass: Added basic data visualization and charting.",
                        "v1.0" to "Initial Release: Basic text-based food tracking capabilities."
                    )
                    items(logs) { (version, notes) ->
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(version, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("Close") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Options") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
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
            fun elasticMod(index: Int) = Modifier.offset(y = (40.dp * (1f - entrance.value) * (index + 1))).alpha(entrance.value)

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // --- APPEARANCE SECTION ---
            item {
                Column(modifier = elasticMod(0)) {
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
                                .clickable { coroutineScope.launch { context.dataStore.edit { it[THEME_MODE_KEY] = key } } }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themePreference == key, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // --- PHASE TOGGLE SECTION ---
            item {
                Column(modifier = elasticMod(1)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Dietary Phase", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    Text(
                        text = "Defines how the Analytics Engine interprets your success metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { coroutineScope.launch { context.dataStore.edit { it[PHASE_MODE_KEY] = "cut" } } }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = phasePreference == "cut", onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Cut Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("A caloric deficit is considered a success.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { coroutineScope.launch { context.dataStore.edit { it[PHASE_MODE_KEY] = "bulk" } } }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = phasePreference == "bulk", onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Bulk Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("A caloric surplus is considered a success.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
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
                        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Context Tags", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
                                    val tagToAdd = newTagText.trim()
                                    coroutineScope.launch {
                                        context.dataStore.edit { prefs ->
                                            val current = prefs[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS
                                            val updatedSet = HashSet(current)
                                            updatedSet.add(tagToAdd)
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
                                    val current = prefs[CUSTOM_TAGS_KEY] ?: DEFAULT_TAGS
                                    val updatedSet = HashSet(current)
                                    updatedSet.remove(tag)
                                    prefs[CUSTOM_TAGS_KEY] = updatedSet
                                }
                            }
                        }
                    ) { Icon(Icons.Filled.Close, "Remove Tag", tint = MaterialTheme.colorScheme.error) }
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
                        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportLauncher.launch("food_tracker_db_${LocalDate.now()}.db") }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text("Export Database", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importLauncher.launch(arrayOf("*/*")) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text("Import Database", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val fakeToday = LocalDate.now()
                                    for (i in 0..29) {
                                        val d = fakeToday.minusDays(i.toLong()).toString()
                                        val isGrind = i % 3 != 0
                                        val isFasting = i % 7 == 0

                                        if (!isFasting) {
                                            dao.insertMeal(MealEntity(UUID.randomUUID().toString(), d, "08:30", "Breakfast", "Oatmeal with whey protein and berries"))
                                            dao.insertMeal(MealEntity(UUID.randomUUID().toString(), d, "13:00", "Lunch", "Grilled chicken breast, sweet potato, and broccoli"))
                                            dao.insertMeal(MealEntity(UUID.randomUUID().toString(), d, "19:30", "Dinner", "Salmon salad with olive oil dressing"))
                                            if (isGrind) dao.insertMeal(MealEntity(UUID.randomUUID().toString(), d, "16:00", "Snack", "Protein shake and an apple"))
                                        } else {
                                            dao.insertMeal(MealEntity(UUID.randomUUID().toString(), d, "19:00", "Dinner", "Massive steak and roasted veg (OMAD)"))
                                        }

                                        val tags = mutableListOf<String>()
                                        if (isGrind) tags.add("Grind") else tags.add("Rest")
                                        if (isFasting) tags.add("Fasting")
                                        if (i % 4 == 0) tags.add("Upper Body Bias")
                                        dao.insertTags(DailyTagEntity(d, tags.joinToString(", ")))

                                        val kcal = if (isFasting) "1800" else if (isGrind) "2600" else "2200"
                                        val def = if (isFasting) "800" else if (isGrind) "500" else "-100"
                                        dao.insertMetric(DailyMetricEntity(d, kcal, def))

                                        val weight = String.format("%.1f", 75.0f + (i * 0.1f))
                                        val bf = String.format("%.1f", 16.0f + (i * 0.05f))
                                        dao.insertMeasurement(MeasurementEntity(d, weight, bf))

                                        val insightText = if (isGrind) "Excellent adherence to macros on a Grind day. Your Upper Body Bias protocol is keeping the deficit high without taxing the legs." else "Rest day caloric surplus noted. Keep an eye on carb intake tomorrow to compensate."
                                        dao.insertInsight(DailyInsightEntity(d, insightText))
                                    }
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "30-Day Test Data Injected!", Toast.LENGTH_LONG).show() }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Text("Seed 30-Day Test Data", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
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
                        modifier = Modifier.fillMaxWidth().clickable { showManual = true }.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("App Manual", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showChangelog = true }.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("View Changelog", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // --- APP VERSION FOOTER ---
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 0.dp, top = 24.dp, end = 0.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Gryzor Food Tracker",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "v4.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}