package com.example.gryzorfoodtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class HabitWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(context)
        val dao = db.mealDao()

        // 1. Fetch the last 7 days of meals to establish the baseline
        val recentMeals = mutableListOf<MealEntity>()
        for (i in 1..7) {
            val dateStr = LocalDate.now().minusDays(i.toLong()).toString()
            recentMeals.addAll(dao.getMealsForDate(dateStr).first())
        }

        if (recentMeals.isEmpty()) return Result.success()

        // 2. Find the most frequent meal combo (Type + Description)
        val mostFrequentHabit = recentMeals
            .groupBy { "${it.type}|${it.description}" }
            .maxByOrNull { it.value.size }

        // 3. If we have a solid habit (logged at least twice recently), trigger the nudge
        if (mostFrequentHabit != null && mostFrequentHabit.value.size >= 2) {
            val topMeal = mostFrequentHabit.value.first()

            // Clean up the description for the notification (remove the AI macros if present)
            val cleanDesc = topMeal.description.substringBefore("[").trim()

            showNotification(topMeal.type, cleanDesc, topMeal.description)
        }

        return Result.success()
    }

    private fun showNotification(type: String, cleanDesc: String, fullDesc: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "habit_channel"

        // Create the Notification Channel (Required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Habit Nudges",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Predictive meal logging suggestions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action 1: Silent Background Log
        val logIntent = Intent(context, HabitReceiver::class.java).apply {
            action = "LOG_HABIT"
            putExtra("habit_type", type)
            putExtra("habit_desc", fullDesc) // Keep the AI macros if they exist
        }
        val logPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Open App to Edit
        val editIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("edit_meal_type", type)
            putExtra("edit_meal_desc", cleanDesc)
        }
        val editPendingIntent = PendingIntent.getActivity(
            context,
            1,
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Time for your usual?")
            .setContentText("Log $type: $cleanDesc")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "Log It", logPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Edit", editPendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}

// The Silent Background Receiver
class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "LOG_HABIT") {
            val type = intent.getStringExtra("habit_type") ?: return
            val desc = intent.getStringExtra("habit_desc") ?: return

            val db = AppDatabase.getDatabase(context)
            val dao = db.mealDao()

            CoroutineScope(Dispatchers.IO).launch {
                val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val dateNow = LocalDate.now().toString()

                dao.insertMeal(
                    MealEntity(
                        id = UUID.randomUUID().toString(),
                        date = dateNow,
                        time = timeNow,
                        type = type,
                        description = desc
                    )
                )

                // Dismiss the notification manually after logging
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(1001)

                // Show a quick toast so the user knows it worked
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$type Logged!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}