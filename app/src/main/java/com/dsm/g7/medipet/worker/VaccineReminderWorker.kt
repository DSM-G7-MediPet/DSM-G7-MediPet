package com.dsm.g7.medipet.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dsm.g7.medipet.data.local.VaccineDao
//import com.google.firebase.functions.dagger.assisted.Assisted
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class VaccineReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val vaccineDao: VaccineDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val currentTime = System.currentTimeMillis()
            // Calculamos 7 días
            val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)
            val targetTime = currentTime + sevenDaysInMillis

            // Consultamos a la base de datos
            val upcomingVaccines = vaccineDao.getUpcomingVaccines(
                startMillis = currentTime,
                endMillis = targetTime
            )

            // Si hay vacunas próximas, disparamos la notificación
            if (upcomingVaccines.isNotEmpty()) {
                mostrarNotificacionLocal(upcomingVaccines.size)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun mostrarNotificacionLocal(cantidad: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "vaccine_reminder_channel"

        // Crear el canal de notificaciones (Requerido para Android 8.0 / API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Recordatorios de Vacunas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificar vacunas próximas a vencer"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Construir la notificación
        val notification = NotificationCompat.Builder(context, channelId)
            // Se usa el ícono por defecto de Android como prueba, luego puedes cambiarlo por el logo de MediPet
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("¡Recordatorio de Vacunación MediPet!")
            .setContentText("Tienes $cantidad vacuna(s) programada(s) para los próximos 7 días.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Hace que la notificación desaparezca al tocarla
            .build()

        // Mostrar la notificación
        notificationManager.notify(1001, notification)
    }
}