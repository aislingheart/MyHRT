package com.example.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getIntExtra("MED_ID", -1)
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("med_reminders", "Medication Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val takeIntent = Intent(context, MarkTakenReceiver::class.java).apply {
            putExtra("MED_ID", medId)
        }
        val takePendingIntent = PendingIntent.getBroadcast(
            context, medId, takeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val uncompletedIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, medId, uncompletedIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "med_reminders")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to take your medication")
            .setContentText("Did you take $medName?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "Mark Taken", takePendingIntent)

        notificationManager.notify(medId, builder.build())
    }
}

class MarkTakenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getIntExtra("MED_ID", -1)
        if (medId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(medId)

            // Use centralized database singleton — no duplicated migrations
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getInstance(context.applicationContext)
                    val dao = database.hrtDao()
                    val dbMeds = dao.getAllMedications().first()
                    val med = dbMeds.find { it.id == medId }
                    if (med != null) {
                        dao.insertMedication(med.copy(lastTakenDateMillis = System.currentTimeMillis()))
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
