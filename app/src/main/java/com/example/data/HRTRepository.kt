package com.example.data

import kotlinx.coroutines.flow.Flow

class HRTRepository(private val dao: HRTDao) {
    val userProfile: Flow<UserProfile?> = dao.getUserProfile()
    val allMedications: Flow<List<Medication>> = dao.getAllMedications()
    val allLogs: Flow<List<DailyLog>> = dao.getAllLogs()
    val allBloodTests: Flow<List<BloodTestResult>> = dao.getAllBloodTests()
    val allMilestoneChecks: Flow<List<MilestoneCheck>> = dao.getAllMilestoneChecks()

    suspend fun saveUserProfile(profile: UserProfile) = dao.insertUserProfile(profile)

    suspend fun addMedication(medication: Medication) = dao.insertMedication(medication)
    
    suspend fun removeMedication(id: Int) = dao.deleteMedication(id)

    suspend fun addLog(log: DailyLog) = dao.insertLog(log)
    
    fun getLogForDay(startOfDay: Long, endOfDay: Long): Flow<DailyLog?> = dao.getLogForDay(startOfDay, endOfDay)
    
    suspend fun addBloodTest(test: BloodTestResult) = dao.insertBloodTest(test)
    suspend fun removeBloodTest(id: Int) = dao.deleteBloodTest(id)
    suspend fun checkMilestone(check: MilestoneCheck) = dao.insertMilestoneCheck(check)
    suspend fun uncheckMilestone(id: String) = dao.deleteMilestoneCheck(id)

    val allChatSessions: Flow<List<ChatSession>> = dao.getAllChatSessions()
    suspend fun addChatSession(session: ChatSession) = dao.insertChatSession(session)
    suspend fun deleteChatSession(id: String) = dao.deleteChatSession(id)
    
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> = dao.getMessagesForSession(sessionId)
    suspend fun addChatMessage(message: ChatMessage) = dao.insertChatMessage(message)
}
