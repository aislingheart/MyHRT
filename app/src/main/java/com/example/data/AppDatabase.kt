package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, Medication::class, DailyLog::class, BloodTestResult::class, MilestoneCheck::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hrtDao(): HRTDao
}
