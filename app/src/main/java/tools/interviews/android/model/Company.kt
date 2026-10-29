package tools.interviews.android.model

import tools.interviews.android.data.account.AccountOwners
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "companies",
    indices = [Index(value = ["serverId"], unique = true), Index(value = ["ownerId"])]
)
data class Company(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = AccountOwners.GUEST,
    val serverId: Int? = null, // Server ID - null means not synced yet
    val name: String
)