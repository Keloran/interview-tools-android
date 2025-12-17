package tools.interviews.android.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "companies",
    indices = [Index(value = ["serverId"], unique = true)]
)
data class Company(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Int? = null, // Server ID - null means not synced yet
    val name: String
)