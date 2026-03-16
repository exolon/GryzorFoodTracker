package com.example.gryzorfoodtracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.map

// Global DataStore Definitions
val Context.dataStore by preferencesDataStore(name = "settings")
val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
val CUSTOM_TAGS_KEY = stringSetPreferencesKey("custom_tags")
val PHASE_MODE_KEY = stringPreferencesKey("phase_mode")
val TARGET_WEIGHT_KEY = stringPreferencesKey("target_weight")
val BANNED_SUGGESTIONS_KEY = stringSetPreferencesKey("banned_suggestions")

// --- V4.3 TAG UPDATE ---
val DEFAULT_TAGS = setOf("Grind", "Recovery", "High Protein", "Upper Body Bias", "Rest")
// -----------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(applicationContext)

        setContent {
            val context = LocalContext.current
            val themePreference by context.dataStore.data
                .map { it[THEME_MODE_KEY] ?: "system" }
                .collectAsState(initial = "system")

            val isSystemDark = isSystemInDarkTheme()

            // --- CUSTOM THEME ENGINE ---
            val oledColorScheme = darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF121212)
            )

            val dimColorScheme = darkColorScheme(
                background = Color(0xFF0F172A),
                surface = Color(0xFF1E293B),
                surfaceVariant = Color(0xFF334155)
            )

            val nordicColorScheme = lightColorScheme(
                background = Color(0xFFFDFBF7),
                surface = Color(0xFFF5F2EB),
                surfaceVariant = Color(0xFFEAE6DA),
                primary = Color(0xFF556B5C),
                secondary = Color(0xFFA76D60),
                onBackground = Color(0xFF2C2C2C),
                onSurface = Color(0xFF2C2C2C)
            )

            val clinicalColorScheme = lightColorScheme(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFF9FAFB),
                surfaceVariant = Color(0xFFF3F4F6),
                primary = Color(0xFF007AFF),
                secondary = Color(0xFFFF2D55),
                onBackground = Color(0xFF000000),
                onSurface = Color(0xFF000000)
            )

            val monochromeColorScheme = lightColorScheme(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFF3F4F6),
                surfaceVariant = Color(0xFFE5E7EB),
                primary = Color(0xFF111827),
                secondary = Color(0xFF4B5563),
                error = Color(0xFFDC2626), // Keep red for deficit alerts
                onBackground = Color(0xFF000000),
                onSurface = Color(0xFF000000)
            )

            val activeColorScheme = when (themePreference) {
                "light" -> lightColorScheme()
                "nordic" -> nordicColorScheme
                "clinical" -> clinicalColorScheme
                "monochrome" -> monochromeColorScheme
                "dark" -> oledColorScheme
                "dim" -> dimColorScheme
                else -> if (isSystemDark) oledColorScheme else lightColorScheme()
            }

            MaterialTheme(
                colorScheme = activeColorScheme
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()
                    var shortcutMealType by remember { mutableStateOf<String?>(intent?.getStringExtra("meal_type")) }

                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        // V5.1: Navigation Transitions for Predictive Back
                        enterTransition = {
                            slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            scaleOut(targetScale = 0.9f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            scaleIn(initialScale = 0.9f, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        composable("home") {
                            FoodTrackerScreen(
                                db = db,
                                themePreference = themePreference,
                                navController = navController,
                                shortcutMealType = shortcutMealType,
                                onShortcutHandled = { shortcutMealType = null }
                            )
                        }
                        composable("analytics") {
                            AnalyticsScreen(
                                navController = navController,
                                db = db
                            )
                        }
                        composable("behavior") {
                            BehaviorScreen(
                                navController = navController,
                                db = db
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                navController = navController,
                                db = db
                            )
                        }
                    }
                }
            }
        }
    }
}