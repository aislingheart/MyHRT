package com.example.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.Medication
import java.util.Calendar

object ReminderUtil {

    fun scheduleAlarms(context: Context, alarmManager: AlarmManager, med: Medication) {
        cancelAlarms(context, alarmManager, med)

        val freq = med.frequency.lowercase()
        val timesPerDay = when {
            freq.contains("twice daily") || freq.contains("twice-daily") || freq.contains("bid") -> 2
            freq.contains("three times") -> 3
            else -> 1
        }
        
        val baseId = med.name.hashCode()

        for (i in 0 until timesPerDay) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("MED_ID", baseId)
                putExtra("MED_NAME", med.name)
            }
            val pi = PendingIntent.getBroadcast(
                context, baseId + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, med.reminderHour)
                set(Calendar.MINUTE, med.reminderMinute)
                set(Calendar.SECOND, 0)
                
                // Add spacing for multiple times a day
                if (timesPerDay == 2 && i == 1) {
                    add(Calendar.HOUR_OF_DAY, 12)
                } else if (timesPerDay == 3) {
                    add(Calendar.HOUR_OF_DAY, 8 * i)
                }
                
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            try {
                val interval = when {
                    freq.contains("weekly") && !freq.contains("twice") -> 7 * AlarmManager.INTERVAL_DAY
                    freq.contains("every 3 days") -> 3 * AlarmManager.INTERVAL_DAY
                    freq.contains("every 5 days") -> 5 * AlarmManager.INTERVAL_DAY
                    freq.contains("monthly") -> 30 * AlarmManager.INTERVAL_DAY
                    else -> AlarmManager.INTERVAL_DAY
                }
                
                val actualInterval = if (timesPerDay > 1) AlarmManager.INTERVAL_DAY else interval

                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    actualInterval,
                    pi
                )
            } catch (e: SecurityException) {
                // Ignore security exceptions for exact alarms; setRepeating handles gracefully
            }
        }
    }

    fun cancelAlarms(context: Context, alarmManager: AlarmManager, med: Medication) {
        val baseId = med.name.hashCode()
        for (i in 0..3) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, baseId + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }
}
