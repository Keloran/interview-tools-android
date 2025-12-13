package tools.interviews.android.data

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
    @Query("SELECT * FROM interviews ORDER BY COALESCE(interviewDate, deadline, applicationDate) DESC")
    fun getAllInterviews(): Flow<List<Interview>>

    @Query("SELECT * FROM interviews WHERE id = :id")
    suspend fun getInterviewById(id: Long): Interview?

    @Query("SELECT DISTINCT companyName FROM interviews ORDER BY companyName ASC")
    fun getAllCompanies(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interview: Interview): Long

    @Update
    suspend fun update(interview: Interview)

    @Delete
    suspend fun delete(interview: Interview)

    @Query("DELETE FROM interviews WHERE id = :id")
    suspend fun deleteById(id: Long)
}
