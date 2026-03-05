package com.example.gryzorfoodtracker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Native Android PDF Generation Engine (v3.9)
 * Generates a high-fidelity Executive Summary with Vector Graphs.
 */
fun generateExecutiveSummaryPdf(
    outputStream: OutputStream,
    phasePreference: String,
    curKcalAvg: Int,
    curDefAvg: Int,
    tagStats: List<TagStat>,
    last14Days: List<String>,
    allMetrics: List<DailyMetricEntity>,
    allMeasurements: List<MeasurementEntity>
) {
    val pdfDocument = PdfDocument()
    // A4 standard size in PostScript points (595 x 842 at 72 DPI)
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas: Canvas = page.canvas

    // --- PAINTS ---
    val titlePaint = Paint().apply { color = Color.BLACK; textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    val subtitlePaint = Paint().apply { color = Color.DKGRAY; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); isAntiAlias = true }
    val headerPaint = Paint().apply { color = Color.BLACK; textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    val textPaint = Paint().apply { color = Color.DKGRAY; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); isAntiAlias = true }
    val boldTextPaint = Paint().apply { color = Color.BLACK; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f; isAntiAlias = true }

    // Graph Paints
    val primaryPaint = Paint().apply { color = Color.parseColor("#007AFF"); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val errorPaint = Paint().apply { color = Color.parseColor("#FF2D55"); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val gridPaint = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 1f; isAntiAlias = true }
    val cardPaint = Paint().apply { color = Color.parseColor("#F9FAFB"); style = Paint.Style.FILL; isAntiAlias = true }

    var currentY = 60f
    val leftMargin = 50f
    val rightMargin = 545f
    val contentWidth = rightMargin - leftMargin

    // --- 1. HEADER ---
    canvas.drawText("Gryzor Food Tracker", leftMargin, currentY, titlePaint)
    currentY += 20f
    val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
    canvas.drawText("Executive Summary • Generated $dateStr", leftMargin, currentY, subtitlePaint)
    currentY += 25f
    canvas.drawLine(leftMargin, currentY, rightMargin, currentY, linePaint)
    currentY += 30f

    // --- 2. VELOCITY & TRAJECTORY ---
    canvas.drawText("Velocity & Trajectory", leftMargin, currentY, headerPaint)
    currentY += 20f
    canvas.drawText("Dietary Phase: ${phasePreference.uppercase()}", leftMargin, currentY, boldTextPaint)
    currentY += 20f
    canvas.drawText("Trailing 7-Day Avg Intake: $curKcalAvg kcal", leftMargin, currentY, textPaint)
    currentY += 20f
    val defLabel = if (phasePreference == "bulk") "Surplus" else "Deficit"
    canvas.drawText("Trailing 7-Day Avg $defLabel: ${Math.abs(curDefAvg)} kcal", leftMargin, currentY, textPaint)
    currentY += 30f

    // --- 3. 14-DAY MACRO TREND GRAPH ---
    canvas.drawText("14-Day Macro Trend (Primary: Kcal, Red: Deficit)", leftMargin, currentY, headerPaint)
    currentY += 15f
    val macroGraphRect = RectF(leftMargin, currentY, rightMargin, currentY + 120f)
    canvas.drawRoundRect(macroGraphRect, 8f, 8f, cardPaint)

    // Draw Graph Grid
    for (i in 1..3) {
        val yLine = currentY + (120f / 4) * i
        canvas.drawLine(leftMargin, yLine, rightMargin, yLine, gridPaint)
    }

    val maxKcal = allMetrics.filter { last14Days.contains(it.date) }.maxOfOrNull { it.totalKcal.toFloatOrNull() ?: 0f }?.coerceAtLeast(2500f) ?: 2500f
    val minDeficit = allMetrics.filter { last14Days.contains(it.date) }.minOfOrNull { it.deficit.toFloatOrNull() ?: 0f }?.coerceAtMost(0f) ?: -500f
    val totalRange = maxKcal - minDeficit
    val stepX = contentWidth / 13f

    val kcalPath = Path()
    val defPath = Path()
    var firstKcal = true
    var firstDef = true

    last14Days.forEachIndexed { index, date ->
        val metric = allMetrics.find { it.date == date }
        val x = leftMargin + (index * stepX)

        val kcal = metric?.totalKcal?.toFloatOrNull()
        if (kcal != null) {
            val y = (currentY + 120f) - (((kcal - minDeficit) / totalRange) * 120f)
            if (firstKcal) { kcalPath.moveTo(x, y); firstKcal = false } else kcalPath.lineTo(x, y)
            canvas.drawCircle(x, y, 3f, primaryPaint)
        }

        val def = metric?.deficit?.toFloatOrNull()
        if (def != null) {
            val y = (currentY + 120f) - (((def - minDeficit) / totalRange) * 120f)
            if (firstDef) { defPath.moveTo(x, y); firstDef = false } else defPath.lineTo(x, y)
            canvas.drawCircle(x, y, 3f, errorPaint)
        }
    }
    canvas.drawPath(kcalPath, primaryPaint)
    canvas.drawPath(defPath, errorPaint)
    currentY += 150f

    // --- 4. BODY COMP TREND GRAPH ---
    canvas.drawText("Body Composition Trend (Primary: Weight, Red: Fat %)", leftMargin, currentY, headerPaint)
    currentY += 15f
    val compGraphRect = RectF(leftMargin, currentY, rightMargin, currentY + 120f)
    canvas.drawRoundRect(compGraphRect, 8f, 8f, cardPaint)

    for (i in 1..3) {
        val yLine = currentY + (120f / 4) * i
        canvas.drawLine(leftMargin, yLine, rightMargin, yLine, gridPaint)
    }

    val maxWeight = 85f; val minWeight = 66f; val rangeW = maxWeight - minWeight
    val maxFat = 25f; val minFat = 10f; val rangeF = maxFat - minFat
    val wPath = Path()
    val fPath = Path()
    var firstW = true
    var firstF = true

    last14Days.forEachIndexed { index, date ->
        val measure = allMeasurements.find { it.date == date }
        val x = leftMargin + (index * stepX)

        val w = measure?.weight?.toFloatOrNull()
        if (w != null) {
            val clampedW = w.coerceIn(minWeight, maxWeight)
            val y = (currentY + 120f) - (((clampedW - minWeight) / rangeW) * 120f)
            if (firstW) { wPath.moveTo(x, y); firstW = false } else wPath.lineTo(x, y)
            canvas.drawCircle(x, y, 3f, primaryPaint)
        }

        val f = measure?.bodyFat?.toFloatOrNull()
        if (f != null) {
            val clampedF = f.coerceIn(minFat, maxFat)
            val y = (currentY + 120f) - (((clampedF - minFat) / rangeF) * 120f)
            if (firstF) { fPath.moveTo(x, y); firstF = false } else fPath.lineTo(x, y)
            canvas.drawCircle(x, y, 3f, errorPaint)
        }
    }
    canvas.drawPath(wPath, primaryPaint)
    canvas.drawPath(fPath, errorPaint)
    currentY += 150f

    // --- 5. BEHAVIORAL COMPLIANCE MATRIX ---
    canvas.drawText("Behavioral Compliance Matrix", leftMargin, currentY, headerPaint)
    currentY += 20f

    if (tagStats.isEmpty()) {
        canvas.drawText("No context tags logged in the trailing period.", leftMargin, currentY, textPaint)
    } else {
        // Table Header
        canvas.drawRoundRect(RectF(leftMargin, currentY, rightMargin, currentY + 25f), 4f, 4f, cardPaint)
        canvas.drawText("Context Tag", leftMargin + 10f, currentY + 17f, boldTextPaint)
        canvas.drawText("Days Logged", leftMargin + 200f, currentY + 17f, boldTextPaint)
        canvas.drawText("Avg Deficit", leftMargin + 320f, currentY + 17f, boldTextPaint)
        canvas.drawText("Success Rate", leftMargin + 420f, currentY + 17f, boldTextPaint)
        currentY += 35f

        // Table Rows
        tagStats.forEach { stat ->
            canvas.drawText(stat.tag, leftMargin + 10f, currentY, boldTextPaint)
            canvas.drawText("${stat.totalDays} days", leftMargin + 200f, currentY, textPaint)
            canvas.drawText("${stat.avgDeficit} kcal", leftMargin + 320f, currentY, textPaint)

            // Color code success rate text
            val rateColor = if (stat.winRate >= 70) primaryPaint.color else if (stat.winRate >= 40) Color.parseColor("#F59E0B") else errorPaint.color
            textPaint.color = rateColor
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${stat.winRate}%", leftMargin + 420f, currentY, textPaint)

            // Reset paint
            textPaint.color = Color.DKGRAY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            currentY += 20f
            canvas.drawLine(leftMargin, currentY - 10f, rightMargin, currentY - 10f, gridPaint)

            if (currentY > 800f) return@forEach
        }
    }

    pdfDocument.finishPage(page)
    pdfDocument.writeTo(outputStream)
    pdfDocument.close()
}