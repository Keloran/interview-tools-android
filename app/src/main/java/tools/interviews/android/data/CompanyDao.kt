package tools.interviews.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tools.interviews.android.model.Company

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun getAllCompanies(): Flow<List<Company>>

    @Query("SELECT * FROM companies ORDER BY name ASC")
    suspend fun getAllCompaniesList(): List<Company>

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getCompanyById(id: Long): Company?

    @Query("SELECT * FROM companies WHERE serverId = :serverId")
    suspend fun getCompanyByServerId(serverId: Int): Company?

    @Query("SELECT * FROM companies WHERE name = :name LIMIT 1")
    suspend fun getCompanyByName(name: String): Company?

    @Query("SELECT * FROM companies WHERE serverId IS NULL")
    suspend fun getUnsyncedCompanies(): List<Company>

    @Query("SELECT serverId FROM companies WHERE serverId IS NOT NULL")
    suspend fun getAllServerIds(): List<Int>

    @Query("SELECT name FROM companies ORDER BY name ASC")
    fun getAllCompanyNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(company: Company): Long

    @Update
    suspend fun update(company: Company)

    @Query("UPDATE companies SET serverId = NULL WHERE id IN (SELECT companyId FROM interviews WHERE companyId IS NOT NULL)")
    suspend fun detachGuestCompanies()

    @Query("DELETE FROM companies WHERE serverId IS NOT NULL")
    suspend fun deleteSynced()

    @Query("DELETE FROM companies")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(company: Company)

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM companies WHERE serverId = :serverId AND id NOT IN (SELECT companyId FROM interviews WHERE companyId IS NOT NULL)")
    suspend fun deleteUnreferencedByServerId(serverId: Int)

    @Query("DELETE FROM companies WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Int)
}