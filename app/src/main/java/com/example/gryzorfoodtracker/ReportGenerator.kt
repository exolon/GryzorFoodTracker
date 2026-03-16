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
        val chronologicalDays = last30Days.reversed() // For graphing left-to-right

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

        // --- EXTRACT DATA FOR VECTOR GRAPHS ---
        val kcalData = chronologicalDays.map { metricsMap[it]?.totalKcal?.toFloatOrNull() }
        val defData = chronologicalDays.map { metricsMap[it]?.deficit?.toFloatOrNull() }
        val weightData = chronologicalDays.map { measurementsMap[it]?.weight?.toFloatOrNull() }
        val fatData = chronologicalDays.map { measurementsMap[it]?.bodyFat?.toFloatOrNull() }

        val loadData = chronologicalDays.map { date ->
            tagsMap[date]?.tags?.split(",")?.find { it.trim().startsWith("Friction:") }?.substringAfter(":")?.trim()?.toFloatOrNull()
        }
        val sleepData = chronologicalDays.map { date ->
            tagsMap[date]?.tags?.split(",")?.find { it.trim().startsWith("Sleep:") }?.substringAfter(":")?.trim()?.toFloatOrNull()
        }

        // --- PURE KOTLIN SVG BUILDER ---
        fun buildSvgGraph(
            data1: List<Float?>, color1: String, name1: String,
            data2: List<Float?>? = null, color2: String? = null, name2: String? = null,
            fixed0To5: Boolean = false
        ): String {
            val width = 800f
            val height = 220f
            val padX = 20f
            val padY = 40f
            val w = width - 2 * padX
            val h = height - 2 * padY

            var svg = """<svg viewBox="0 0 $width $height" style="width: 100%; height: auto; background: #fff; border-radius: 12px; border: 1px solid #eaeaea;">"""

            // Gridlines
            svg += """<line x1="$padX" y1="${padY}" x2="${padX+w}" y2="${padY}" stroke="#f4f4f4" stroke-width="1"/>"""
            svg += """<line x1="$padX" y1="${padY+h/2}" x2="${padX+w}" y2="${padY+h/2}" stroke="#f4f4f4" stroke-width="1"/>"""
            svg += """<line x1="$padX" y1="${padY+h}" x2="${padX+w}" y2="${padY+h}" stroke="#f4f4f4" stroke-width="1"/>"""

            fun getPath(data: List<Float?>, color: String, isArea: Boolean): String {
                val nonNulls = data.filterNotNull()
                if (nonNulls.isEmpty()) return ""

                val min = if (fixed0To5) 0f else nonNulls.minOrNull() ?: 0f
                val max = if (fixed0To5) 5f else nonNulls.maxOrNull() ?: 1f
                val range = if (max == min) 1f else max - min

                var d = ""
                val step = w / (data.size - 1).coerceAtLeast(1)
                var first = true
                var startX = 0f
                var lastX = 0f

                var circles = ""

                for (i in data.indices) {
                    val v = data[i]
                    if (v != null) {
                        val x = padX + i * step
                        val y = padY + h - ((v - min) / range * h)
                        if (first) {
                            d += "M $x $y "
                            startX = x
                            first = false
                        } else {
                            d += "L $x $y "
                        }
                        lastX = x
                        circles += """<circle cx="$x" cy="$y" r="4" fill="#fff" stroke="$color" stroke-width="2"/>"""
                    }
                }

                var result = ""
                if (!first) {
                    if (isArea) {
                        val areaD = d + "L $lastX ${padY+h} L $startX ${padY+h} Z"
                        result += """<path d="$areaD" fill="$color" fill-opacity="0.1"/>"""
                    }
                    result += """<path d="$d" fill="none" stroke="$color" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>"""
                    result += circles
                }
                return result
            }

            if (data2 != null && color2 != null) {
                svg += getPath(data2, color2, false)
            }
            svg += getPath(data1, color1, true)

            // Legend
            svg += """<rect x="${padX}" y="15" width="12" height="12" rx="3" fill="$color1"/>"""
            svg += """<text x="${padX+20}" y="25" font-family="sans-serif" font-size="12" font-weight="bold" fill="#333">$name1</text>"""

            if (name2 != null && color2 != null) {
                svg += """<rect x="${padX+120}" y="15" width="12" height="12" rx="3" fill="$color2"/>"""
                svg += """<text x="${padX+140}" y="25" font-family="sans-serif" font-size="12" font-weight="bold" fill="#333">$name2</text>"""
            }

            svg += "</svg>"
            return svg
        }

        val macroSvg = buildSvgGraph(kcalData, "#005A9C", "Total Intake", defData, "#D93025", "Deficit/Surplus")
        val compSvg = buildSvgGraph(weightData, "#005A9C", "Weight (kg)", fatData, "#F0A500", "Body Fat (%)")
        val behaviorSvg = buildSvgGraph(loadData, "#D93025", "Cognitive Load", sleepData, "#005A9C", "Sleep Quality", fixed0To5 = true)

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
                    
                    .hero-grid { display: flex; gap: 20px; margin-bottom: 30px; }
                    .hero-card { flex: 1; background: #fff; padding: 24px; border-radius: 12px; border: 1px solid #eaeaea; text-align: center; }
                    .hero-card.success { border-bottom: 4px solid #2E7D32; }
                    .hero-card p { font-size: 12px; color: #666; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; margin-bottom: 8px; }
                    .hero-card h2 { font-size: 32px; font-weight: 800; color: #111; }
                    
                    .chart-title { font-size: 12px; color: #666; text-transform: uppercase; letter-spacing: 1px; font-weight: 800; margin-bottom: 8px; }
                    .chart-row { display: flex; gap: 20px; margin-bottom: 20px; }
                    .chart-col { flex: 1; }
                    .chart-full { margin-bottom: 40px; page-break-after: always; }
                    
                    /* Smart Page Breaks */
                    .day-block { background: #fff; border-radius: 12px; border: 1px solid #eaeaea; padding: 20px; margin-bottom: 20px; }
                    .day-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; page-break-after: avoid; }
                    .day-date { font-weight: 800; font-size: 18px; }
                    .day-tags { font-size: 12px; background: #f0f5f0; color: #2E7D32; padding: 4px 10px; border-radius: 12px; font-weight: 600; }
                    
                    .macros { display: flex; gap: 16px; margin-bottom: 16px; page-break-inside: avoid; }
                    .macro-item { font-size: 14px; color: #444; }
                    .macro-item strong { color: #111; }
                    
                    .insight { background: #f8f9fa; border-left: 4px solid #6c757d; padding: 12px 16px; font-size: 14px; color: #555; margin-bottom: 16px; border-radius: 0 8px 8px 0; font-style: italic; page-break-inside: avoid; }
                    
                    table { width: 100%; border-collapse: collapse; font-size: 13px; }
                    tr { page-break-inside: avoid; }
                    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f0f0f0; }
                    th { font-weight: 600; color: #888; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; }
                    tr:last-child td { border-bottom: none; }
                    .time-col { width: 80px; color: #666; }
                    .type-col { width: 100px; font-weight: 600; }
                </style>
            </head>
            <body>
        """.trimIndent())

        // HEADER
        sb.append("""
            <div class="header">
                <h1>Executive Summary</h1>
                <p>30-Day Trajectory: ${LocalDate.parse(last30Days.first()).format(DateTimeFormatter.ofPattern("MMM d"))} — ${LocalDate.parse(last30Days.last()).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}</p>
            </div>
        """.trimIndent())

        // HERO METRICS
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

        // PURE SVG CHARTS GRID (Page 1)
        sb.append("""
            <div class="chart-row">
                <div class="chart-col">
                    <div class="chart-title">Macro Trajectory</div>
                    $macroSvg
                </div>
                <div class="chart-col">
                    <div class="chart-title">Body Composition</div>
                    $compSvg
                </div>
            </div>
            <div class="chart-full">
                <div class="chart-title">System Friction vs. Sleep Quality</div>
                $behaviorSvg
            </div>
        """.trimIndent())

        // THE LOG DIARY (Pages 2+)
        last30Days.forEach { dateStr ->
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