package com.example.gryzorfoodtracker

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate

class MacroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MacroWidget()
}

class MacroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val today = LocalDate.now().toString()

        val todayMeals = db.mealDao().getMealsForDate(today).firstOrNull() ?: emptyList()
        val todayTags = db.mealDao().getTagsForDate(today).firstOrNull()?.tags?.takeIf { it.isNotBlank() }

        val mealCount = todayMeals.size
        val lastMeal = todayMeals.lastOrNull()
        val lastMealText = if (lastMeal != null) "${lastMeal.type} @ ${lastMeal.time}" else "No meals logged yet"

        val allMetrics = db.mealDao().getAllMetrics().firstOrNull() ?: emptyList()
        val todayDate = LocalDate.now()
        val currentWeekDates = (0..6).map { todayDate.minusDays(it.toLong()).toString() }
        val prevWeekDates = (7..13).map { todayDate.minusDays(it.toLong()).toString() }

        val currentDef = allMetrics.filter { currentWeekDates.contains(it.date) }.mapNotNull { it.deficit.toDoubleOrNull() }
        val prevDef = allMetrics.filter { prevWeekDates.contains(it.date) }.mapNotNull { it.deficit.toDoubleOrNull() }

        val curDefAvg = if (currentDef.isNotEmpty()) currentDef.average().toInt() else 0
        val prevDefAvg = if (prevDef.isNotEmpty()) prevDef.average().toInt() else 0
        val diffDef = curDefAvg - prevDefAvg
        val arrowDef = if (diffDef > 0) "↑" else if (diffDef < 0) "↓" else "="

        // Create an explicit, aggressive intent to force the app open
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(Color(0x990F172A))
                        .clickable(actionStartActivity(launchIntent)) // <--- Bulletproof Tap Target
                        .padding(16.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    Text(
                        text = "TODAY's STATUS",
                        style = TextStyle(color = ColorProvider(day = Color.LightGray, night = Color.LightGray), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(8.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "$mealCount",
                            style = TextStyle(color = ColorProvider(day = Color(0xFF60A5FA), night = Color(0xFF60A5FA)), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.width(12.dp))
                        Column {
                            Text(
                                text = if (mealCount == 1) "Meal Logged" else "Meals Logged",
                                style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = lastMealText,
                                style = TextStyle(color = ColorProvider(day = Color.Gray, night = Color.Gray), fontSize = 12.sp)
                            )
                        }
                    }

                    Spacer(GlanceModifier.height(12.dp))

                    if (todayTags != null) {
                        Text(
                            text = "Tags: $todayTags",
                            style = TextStyle(color = ColorProvider(day = Color(0xFFFBBF24), night = Color(0xFFFBBF24)), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        )
                        Spacer(GlanceModifier.height(4.dp))
                    }

                    Text(
                        text = "7D Trailing Deficit: $curDefAvg $arrowDef",
                        style = TextStyle(color = ColorProvider(day = Color(0xFFF87171), night = Color(0xFFF87171)), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}