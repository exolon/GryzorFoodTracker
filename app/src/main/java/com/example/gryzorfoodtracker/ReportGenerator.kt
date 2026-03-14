package com.example.gryzorfoodtracker

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

suspend fun generateAndPrintReport(
    context: Context,
    dao: MealDao,
    phasePreference: String,
    customTags: Set<String>
) {
    val htmlContent = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val last30Days = (29 downTo 0).map { today.minusDays(it.toLong()).toString() }

        // V5.0 FIXED: Safely unwrapping Flow<T> to a static snapshot using .first()
        val metricsMap = last30Days.associateWith { dao.getMetricsForDate(it).first() }
        val measurementsMap = last30Days.associateWith { dao.getMeasurementForDate(it).first() }
        val tagsMap = last30Days.associateWith { dao.getTagsForDate(it).first() }
        val mealsMap = last30Days.associateWith { dao.getMealsForDate(it).first() }
        val insightsMap = last30Days.associateWith { dao.getInsightForDate(it).first() }

        val validMetrics = metricsMap.values.filterNotNull()
        val totalDaysLogged = validMetrics.size

        val successDays = validMetrics.count {
            val def = it.deficit.toDoubleOrNull() ?: 0.0
            if (phasePreference == "bulk") def < 0 else def > 0
        }
        val successRate = if (totalDaysLogged > 0) (successDays.toFloat() / totalDaysLogged * 100).toInt() else 0

        val startWeight = measurementsMap.values.filterNotNull().minByOrNull { it.date }?.weight?.toFloatOrNull()
        val endWeight = measurementsMap.values.filterNotNull().maxByOrNull { it.date }?.weight?.toFloatOrNull()
        val weightDelta = if (startWeight != null && endWeight != null) endWeight - startWeight else null

        val sb = StringBuilder()

        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800&display=swap" rel="stylesheet">
                <style>
                    body { font-family: 'Inter', sans-serif; color: #1c1c1c; line-height: 1.5; margin: 0; padding: 40px; background-color: #f9f9f9; }
                    h1, h2, h3 { margin: 0; }
                    .header { text-align: center; margin-bottom: 40px; }
                    .header h1 { font-size: 28px; font-weight: 800; letter-spacing: -0.5px; }
                    .header p { color: #666; font-size: 14px; margin-top: 4px; }
                    
                    .hero-grid { display: flex; gap: 20px; margin-bottom: 40px; }
                    .hero-card { flex: 1; background: #fff; padding: 24px; border-radius: 16px; border: 1px solid #eaeaea; text-align: center; }
                    .hero-card.success { border-bottom: 4px solid #2E7D32; }
                    .hero-card p { font-size: 12px; color: #666; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; margin-bottom: 8px; }
                    .hero-card h2 { font-size: 32px; font-weight: 800; color: #111; }
                    
                    .day-block { background: #fff; border-radius: 16px; border: 1px solid #eaeaea; padding: 24px; margin-bottom: 24px; page-break-inside: avoid; }
                    .day-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }
                    .day-date { font-weight: 800; font-size: 18px; }
                    .day-tags { font-size: 12px; background: #f0f5f0; color: #2E7D32; padding: 4px 10px; border-radius: 12px; font-weight: 600; }
                    
                    .macros { display: flex; gap: 16px; margin-bottom: 16px; }
                    .macro-item { font-size: 14px; color: #444; }
                    .macro-item strong { color: #111; }
                    
                    .insight { background: #f8f9fa; border-left: 4px solid #6c757d; padding: 12px 16px; font-size: 14px; color: #555; margin-bottom: 16px; border-radius: 0 8px 8px 0; font-style: italic; }
                    
                    table { width: 100%; border-collapse: collapse; font-size: 13px; }
                    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f0f0f0; }
                    th { font-weight: 600; color: #888; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; }
                    tr:last-child td { border-bottom: none; }
                    .time-col { width: 80px; color: #666; }
                    .type-col { width: 100px; font-weight: 600; }
                </style>
            </head>
            <body>
        """.trimIndent())

        sb.append("""
            <div class="header">
                <h1>Executive Summary</h1>
                <p>30-Day Trajectory: ${LocalDate.parse(last30Days.first()).format(DateTimeFormatter.ofPattern("MMM d"))} — ${LocalDate.parse(last30Days.last()).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}</p>
            </div>
        """.trimIndent())

        sb.append("""
            <div class="hero-grid">
                <div class="hero-card success">
                    <p>Adherence</p>
                    <h2>$successRate%</h2>
                </div>
                <div class="hero-card">
                    <p>Weight Delta</p>
                    <h2>${if (weightDelta != null) String.format("%+.1f kg", weightDelta) else "--"}</h2>
                </div>
                <div class="hero-card">
                    <p>Days Logged</p>
                    <h2>$totalDaysLogged / 30</h2>
                </div>
            </div>
        """.trimIndent())

        last30Days.reversed().forEach { dateStr ->
            val meals = mealsMap[dateStr]
            val metrics = metricsMap[dateStr]
            val insight = insightsMap[dateStr]
            val tags = tagsMap[dateStr]
            val comp = measurementsMap[dateStr]

            if (!meals.isNullOrEmpty() || metrics != null || !insight?.insight.isNullOrBlank()) {
                val formattedDate = LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

                sb.append("<div class=\"day-block\">")
                sb.append("""
                    <div class="day-header">
                        <div class="day-date">$formattedDate</div>
                        ${if (!tags?.tags.isNullOrBlank()) "<div class=\"day-tags\">${tags?.tags}</div>" else ""}
                    </div>
                """.trimIndent())

                val macroParts = mutableListOf<String>()
                if (!metrics?.totalKcal.isNullOrBlank()) macroParts.add("Intake: <strong>${metrics?.totalKcal}</strong>")
                if (!metrics?.deficit.isNullOrBlank()) macroParts.add("Deficit: <strong>${metrics?.deficit}</strong>")
                if (!comp?.weight.isNullOrBlank()) macroParts.add("Weight: <strong>${comp?.weight}kg</strong>")
                if (!comp?.bodyFat.isNullOrBlank()) macroParts.add("Fat: <strong>${comp?.bodyFat}%</strong>")

                if (macroParts.isNotEmpty()) {
                    sb.append("<div class=\"macros\">")
                    macroParts.forEach { sb.append("<div class=\"macro-item\">$it</div>") }
                    sb.append("</div>")
                }

                if (!insight?.insight.isNullOrBlank()) {
                    sb.append("<div class=\"insight\">\"${insight?.insight}\"</div>")
                }

                if (!meals.isNullOrEmpty()) {
                    sb.append("<table>")
                    sb.append("<tr><th class=\"time-col\">Time</th><th class=\"type-col\">Type</th><th>Description</th></tr>")
                    meals.sortedBy { it.time }.forEach { meal ->
                        sb.append("""
                            <tr>
                                <td class="time-col">${meal.time}</td>
                                <td class="type-col">${meal.type}</td>
                                <td>${meal.description}</td>
                            </tr>
                        """.trimIndent())
                    }
                    sb.append("</table>")
                }
                sb.append("</div>")
            }
        }

        sb.append("</body></html>")
        sb.toString()
    }

    withContext(Dispatchers.Main) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Gryzor_Report_${LocalDate.now()}"
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                val builder = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)

                printManager.print(jobName, printAdapter, builder.build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null)
    }
}