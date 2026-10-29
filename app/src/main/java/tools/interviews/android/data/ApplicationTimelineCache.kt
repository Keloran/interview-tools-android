package tools.interviews.android.data

import androidx.room.*

@Entity(tableName = "application_timelines", primaryKeys = ["ownerId", "applicationId"])
data class ApplicationTimelineCache(val ownerId: String, val applicationId: Int, val json: String)

@Dao
interface ApplicationTimelineDao {
    @Query("SELECT * FROM application_timelines WHERE ownerId = :owner AND applicationId = :id")
    suspend fun get(owner: String, id: Int): ApplicationTimelineCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: ApplicationTimelineCache)
    @Query("DELETE FROM application_timelines WHERE ownerId = :owner")
    suspend fun deleteOwner(owner: String)
}
