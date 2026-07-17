package com.dsm.g7.medipet.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class VaccineReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_VACCINE_NAME = "vaccine_name"
        const val KEY_PET_NAME = "pet_name"
    }

    override suspend fun doWork(): Result {
        return try {
            val vaccineName = inputData.getString(KEY_VACCINE_NAME) ?: "vacuna"
            val petName = inputData.getString(KEY_PET_NAME) ?: "tu mascota"
            showNotification(vaccineName, petName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(vaccineName: String, petName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "vaccine_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Recordatorios de Vacunas", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Recuerda vacunar a tus mascotas a tiempo" }
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Vacuna próxima — $petName")
            .setContentText("La vacuna '$vaccineName' vence en 7 días. ¡No olvides agendar la cita!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("La vacuna '$vaccineName' de $petName vence en 7 días. Agenda la cita con tu veterinario pronto."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(("vaccine_$vaccineName").hashCode(), notification)
    }
}
