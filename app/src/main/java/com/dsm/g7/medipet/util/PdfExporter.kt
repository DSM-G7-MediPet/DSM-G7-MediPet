package com.dsm.g7.medipet.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.dsm.g7.medipet.data.local.MedicalRecord
import com.dsm.g7.medipet.data.local.Pet
import com.dsm.g7.medipet.data.local.Vaccine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PdfExporter {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 22f
        isFakeBoldText = true
    }

    private val headerPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 16f
        isFakeBoldText = true
    }

    private val bodyPaint = Paint().apply {
        color = Color.BLACK
        textSize = 13f
    }

    private val labelPaint = Paint().apply {
        color = Color.GRAY
        textSize = 11f
    }

    private val separatorPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
    }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 20f

    fun exportPdf(
        context: Context,
        pet: Pet,
        records: List<MedicalRecord>,
        vaccines: List<Vaccine>
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = MARGIN + 30f

        // Page 1: Title + pet data + vaccines
        canvas.drawText("Historial Médico — ${pet.name}", MARGIN, y, titlePaint)
        y += 30f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
        y += 20f

        canvas.drawText("Datos de la mascota", MARGIN, y, headerPaint)
        y += LINE_HEIGHT + 4f

        canvas.drawText("Especie: ${pet.species}", MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
        canvas.drawText("Raza: ${pet.breed}", MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
        canvas.drawText("Edad: ${pet.ageYears} años", MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
        canvas.drawText("Peso: ${pet.weightKg} kg", MARGIN, y, bodyPaint)
        y += LINE_HEIGHT + 10f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
        y += 20f

        canvas.drawText("Vacunas", MARGIN, y, headerPaint)
        y += LINE_HEIGHT + 4f

        if (vaccines.isEmpty()) {
            canvas.drawText("Sin vacunas registradas", MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        } else {
            for (vaccine in vaccines) {
                if (y > PAGE_HEIGHT - MARGIN - 40f) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                }
                val status = if (vaccine.isApplied) "Aplicada" else "Pendiente"
                val dateStr = dateFormatter.format(Date(vaccine.dateMillis))
                canvas.drawText(
                    "• ${vaccine.name} (${vaccine.type}) — $dateStr — $status",
                    MARGIN, y, bodyPaint
                )
                y += LINE_HEIGHT
                if (vaccine.vetName.isNotBlank()) {
                    canvas.drawText("  Veterinario: ${vaccine.vetName}", MARGIN, y, labelPaint)
                    y += LINE_HEIGHT
                }
            }
        }

        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
        y += 20f

        // Medical records — may need more pages
        canvas.drawText("Historial de Consultas", MARGIN, y, headerPaint)
        y += LINE_HEIGHT + 4f

        if (records.isEmpty()) {
            canvas.drawText("Sin consultas registradas", MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        } else {
            for (record in records) {
                val neededHeight = 7 * LINE_HEIGHT + 20f
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                    canvas.drawText("Historial de Consultas (cont.)", MARGIN, y, headerPaint)
                    y += LINE_HEIGHT + 4f
                }

                val dateStr = dateFormatter.format(Date(record.dateMillis))
                canvas.drawText("Fecha: $dateStr", MARGIN, y, labelPaint)
                y += LINE_HEIGHT
                canvas.drawText("Diagnóstico: ${record.diagnosis}", MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
                if (record.treatment.isNotBlank()) {
                    canvas.drawText("Tratamiento: ${record.treatment}", MARGIN, y, bodyPaint)
                    y += LINE_HEIGHT
                }
                if (record.medications.isNotBlank()) {
                    canvas.drawText("Medicamentos: ${record.medications}", MARGIN, y, bodyPaint)
                    y += LINE_HEIGHT
                }
                if (record.vetName.isNotBlank()) {
                    canvas.drawText("Veterinario: ${record.vetName}", MARGIN, y, labelPaint)
                    y += LINE_HEIGHT
                }
                y += 8f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
                y += 14f
            }
        }

        document.finishPage(page)

        val file = File(context.filesDir, "historial_${pet.name}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        // Try to open PDF directly for viewing
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            // No PDF viewer installed, offer to share/download
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Abrir historial PDF"))
        }
    }
}
