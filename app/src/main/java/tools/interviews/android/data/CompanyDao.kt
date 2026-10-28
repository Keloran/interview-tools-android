package tools.interviews.android.data

import tools.interviews.android.data.account.AccountOwners
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
    @Query("SELECT * FROM companies WHERE ownerId = :ownerId ORDER BY name ASC")
    fun getAllCompanies(ownerId: String = AccountOwners.GUEST): Flow<List<Company>>

    @Query("SELECT * FROM companies WHERE ownerId = :ownerId ORDER BY name ASC")
    suspend fun getAllCompaniesList(ownerId: String = AccountOwners.GUEST): List<Company>

    @Query("SELECT * FROM companies WHERE id = :id AND ownerId = :ownerId")
    suspend fun getCompanyById(id: Long, ownerId: String = AccountOwners.GUEST): Company?

    @Query("SELECT * FROM companies WHERE serverId = :serverId AND ownerId = :ownerId")
    suspend fun getCompanyByServerId(serverId: Int, ownerId: String = AccountOwners.GUEST): Company?

    @Query("SELECT * FROM companies WHERE name = :name AND ownerId = :ownerId LIMIT 1")
    suspend fun getCompanyByName(name: String, ownerId: String = AccountOwners.GUEST): Company?

    @Query("SELECT * FROM companies WHERE serverId IS NULL AND ownerId = :ownerId")
    suspend fun getUnsyncedCompanies(ownerId: String = AccountOwners.GUEST): List<Company>

    @Query("SELECT serverId FROM companies WHERE serverId IS NOT NULL AND ownerId = :ownerId")
    suspend fun getAllServerIds(ownerId: String = AccountOwners.GUEST): List<Int>

    @Query("SELECT name FROM companies WHERE ownerId = :ownerId ORDER BY name ASC")
    fun getAllCompanyNames(ownerId: String = AccountOwners.GUEST): Flow<List<String>>

    @Query("SELECT * FROM companies WHERE ownerId = 'legacy' AND serverId = :serverId LIMIT 1")
    suspend fun getLegacyByServerId(serverId: Int): Company?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(company: Company): Long

    @Update
    suspend fun update(company: Company)

    @Query("UPDATE companies SET serverId = NULL, ownerId = 'legacy' WHERE ownerId = :ownerId AND id IN (SELECT companyId FROM interviews WHERE companyId IS NOT NULL)")
    suspend fun detachGuestCompanies(ownerId: String = AccountOwners.GUEST)

    @Query("DELETE FROM companies WHERE (ownerId = :ownerId OR ownerId = 'legacy') AND id NOT IN (SELECT companyId FROM interviews WHERE companyId IS NOT NULL)")
    suspend fun deleteSynced(ownerId: String = AccountOwners.GUEST)

    @Query("DELETE FROM companies")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(company: Company)

    @Query("DELETE FROM companies WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteById(id: Long, ownerId: String = AccountOwners.GUEST)

    @Query("DELETE FROM companies WHERE serverId = :serverId AND ownerId = :ownerId AND id NOT IN (SELECT companyId FROM interviews WHERE companyId IS NOT NULL)")
    suspend fun deleteUnreferencedByServerId(serverId: Int, ownerId: String = AccountOwners.GUEST)

    @Query("DELETE FROM companies WHERE serverId = :serverId AND ownerId = :ownerId")
    suspend fun deleteByServerId(serverId: Int, ownerId: String = AccountOwners.GUEST)
}