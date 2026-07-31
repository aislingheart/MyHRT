package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val regimenType: String, // "Feminizing" or "Masculinizing"
    val startDateMillis: Long,
    val isDarkTheme: Boolean? = null,
    val useDynamicColor: Boolean = true,
    val useOnDeviceAi: Boolean = false,
    val apiKey: String? = null,
    val selectedAiModel: String = "gemini-3.5-flash",
    val userName: String = "Friend",
    val isAiEnabled: Boolean = true
)

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val method: String = "Oral", // Oral, Injection, Gel, Patch
    val dose: String,
    val frequency: String, // e.g., "Daily", "Weekly"
    val nextRenewalDateMillis: Long?,
    val isReminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val lastTakenDateMillis: Long = 0L,
    val startDateMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateMillis: Long,
    val notes: String = "",
    val mood: String = "",
    val symptomFlags: String = "" // Comma separated tags
)

@Entity(tableName = "blood_tests")
data class BloodTestResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateMillis: Long,
    val e2Level: Float?, // pg/mL
    val tLevel: Float? // ng/dL
)

@Entity(tableName = "milestone_checks")
data class MilestoneCheck(
    @PrimaryKey val milestoneId: String,
    val achievedDateMillis: Long
)

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val sourcesQuery: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)
