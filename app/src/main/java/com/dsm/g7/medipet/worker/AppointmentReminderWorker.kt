package com.dsm.g7.medipet.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AppointmentReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val petName = inputData.getString("petName") ?: "Tu mascota"
        val reason  = inputData.getString("reason")  ?: ""
        val vetName = inputData.getString("vetName") ?: ""

        val manager   = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "appointment_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Recordatorios de Citas", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Recuerda tus citas veterinarias próximas" }
            )
        }

        val body = buildString {
            append("La cita de $petName comienza en 1 hora")
            if (reason.isNotBlank())  append(" — $reason")
            if (vetName.isNotBlank()) append(" (Vet: $vetName)")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("¡Cita en 1 hora! — MediPet")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }
}
