package tools.interviews.android.data

import tools.interviews.android.data.account.AccountOwners
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tools.interviews.android.model.Interview

@Dao
interface InterviewDao {
    @Query("SELECT * FROM interviews WHERE ownerId = :ownerId ORDER BY COALESCE(interviewInstant, deadlineInstant, offerRespondBy, interviewDate, deadline, applicationDate) DESC")
    fun getAllInterviews(ownerId: String = AccountOwners.GUEST): Flow<List<Interview>>

    @Query("SELECT * FROM interviews WHERE id = :id AND ownerId = :ownerId")
    suspend fun getInterviewById(id: Long, ownerId: String = AccountOwners.GUEST): Interview?

    @Query("SELECT * FROM interviews WHERE id = :id AND ownerId = :ownerId")
    fun observeById(id: Long, ownerId: String): Flow<Interview?>

    @Query("SELECT * FROM interviews WHERE serverId = :serverId AND ownerId = :ownerId")
    suspend fun getInterviewByServerId(serverId: Int, ownerId: String = AccountOwners.GUEST): Interview?

    @Query("SELECT * FROM interviews WHERE serverId IS NULL AND ownerId = :ownerId")
    suspend fun getUnsyncedInterviews(ownerId: String = AccountOwners.GUEST): List<Interview>

    @Query("SELECT * FROM interviews WHERE ownerId = :ownerId AND (serverId IS NULL OR pendingChanges IS NOT NULL) ORDER BY id")
    suspend fun pending(ownerId: String): List<Interview>

    @Query("SELECT COUNT(*) FROM interviews WHERE ownerId = :ownerId AND (serverId IS NULL OR pendingChanges IS NOT NULL)")
    fun pendingCount(ownerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM interviews WHERE ownerId = :ownerId AND syncIssue IS NOT NULL")
    fun failedCount(ownerId: String): Flow<Int>

    @Query("SELECT DISTINCT companyName FROM interviews WHERE ownerId = :ownerId ORDER BY companyName ASC")
    fun getAllCompanies(ownerId: String = AccountOwners.GUEST): Flow<List<String>>

    @Query("SELECT serverId FROM interviews WHERE serverId IS NOT NULL AND ownerId = :ownerId")
    suspend fun getAllServerIds(ownerId: String = AccountOwners.GUEST): List<Int>

    @Query("SELECT * FROM interviews WHERE ownerId = 'legacy' AND serverId = :serverId LIMIT 1")
    suspend fun getLegacyByServerId(serverId: Int): Interview?

    @Query("SELECT * FROM interviews WHERE ownerId = :ownerId AND serverId IS NULL")
    suspend fun localOnly(ownerId: String): List<Interview>

    @Query("SELECT COUNT(*) FROM interviews WHERE ownerId IN ('guest', 'legacy') AND serverId IS NULL")
    fun localImportCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM interviews WHERE ownerId = 'legacy'")
    fun legacyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM interviews WHERE ownerId = 'legacy' AND serverId IS NULL")
    fun legacyLocalCount(): Flow<Int>

    @Query("DELETE FROM interviews WHERE ownerId = :ownerId")
    suspend fun deleteOwner(ownerId: String)

    @Query("DELETE FROM interviews WHERE ownerId IN ('guest', 'legacy') AND serverId IS NULL")
    suspend fun deleteGuestLocal()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interview: Interview): Long

    @Update
    suspend fun update(interview: Interview)

    @Query("DELETE FROM interviews WHERE (ownerId = :ownerId OR ownerId = 'legacy') AND serverId IS NOT NULL")
    suspend fun deleteSynced(ownerId: String = AccountOwners.GUEST)

    @Query("DELETE FROM interviews")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(interview: Interview)

    @Query("DELETE FROM interviews WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteById(id: Long, ownerId: String = AccountOwners.GUEST)

    @Query("DELETE FROM interviews WHERE serverId = :serverId AND ownerId = :ownerId")
    suspend fun deleteByServerId(serverId: Int, ownerId: String = AccountOwners.GUEST)
}
