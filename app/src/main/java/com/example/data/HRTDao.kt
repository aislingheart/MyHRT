package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HRTDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("SELECT * FROM medications")
    fun getAllMedications(): Flow<List<Medication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication)
    
    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedication(id: Int)

    @Query("SELECT * FROM daily_logs ORDER BY dateMillis DESC")
    fun getAllLogs(): Flow<List<DailyLog>>
    
    @Query("SELECT * FROM daily_logs WHERE dateMillis >= :startOfDay AND dateMillis < :endOfDay ORDER BY dateMillis DESC LIMIT 1")
    fun getLogForDay(startOfDay: Long, endOfDay: Long): Flow<DailyLog?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DailyLog)

    @Query("SELECT * FROM blood_tests ORDER BY dateMillis DESC")
    fun getAllBloodTests(): Flow<List<BloodTestResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodTest(bloodTest: BloodTestResult)

    @Query("DELETE FROM blood_tests WHERE id = :id")
    suspend fun deleteBloodTest(id: Int)

    @Query("SELECT * FROM milestone_checks")
    fun getAllMilestoneChecks(): Flow<List<MilestoneCheck>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestoneCheck(check: MilestoneCheck)

    @Query("DELETE FROM milestone_checks WHERE milestoneId = :id")
    suspend fun deleteMilestoneCheck(id: String)
}
