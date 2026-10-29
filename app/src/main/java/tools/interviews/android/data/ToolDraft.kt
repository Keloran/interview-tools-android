package tools.interviews.android.data

import androidx.room.*

@Entity(tableName = "tool_drafts", primaryKeys = ["ownerId", "draftKey"])
data class ToolDraft(val ownerId: String, val draftKey: String, val json: String)

@Dao
interface ToolDraftDao {
    @Query("SELECT * FROM tool_drafts WHERE ownerId=:owner AND draftKey=:key")
    suspend fun get(owner: String, key: String): ToolDraft?

    @Query("SELECT * FROM tool_drafts WHERE ownerId=:owner AND draftKey LIKE :prefix || '%' ORDER BY draftKey")
    suspend fun prefix(owner: String, prefix: String): List<ToolDraft>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(draft: ToolDraft)

    @Query("DELETE FROM tool_drafts WHERE ownerId=:owner AND draftKey=:key")
    suspend fun delete(owner: String, key: String)

    @Query("DELETE FROM tool_drafts WHERE ownerId=:owner") suspend fun deleteOwner(owner: String)
}
