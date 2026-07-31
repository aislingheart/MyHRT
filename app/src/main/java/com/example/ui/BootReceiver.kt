package com.example.ui

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getInstance(context.applicationContext)
                    val meds = database.hrtDao().getAllMedications().first()
                    
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    
                    meds.forEach { med ->
                        if (med.isReminderEnabled) {
                            ReminderUtil.scheduleAlarms(context, alarmManager, med)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
