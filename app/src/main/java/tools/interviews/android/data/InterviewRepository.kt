package tools.interviews.android.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import tools.interviews.android.data.account.AccountOperationGate
import kotlinx.coroutines.flow.Flow
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewRepository(
    private val interviewDao: InterviewDao,
    private val companyDao: CompanyDao,
    private val gate: AccountOperationGate = AccountOperationGate()
) {
    val allInterviews: Flow<List<Interview>> = gate.isBlocked.flatMapLatest { if (it) flowOf(emptyList()) else interviewDao.getAllInterviews() }
    val allCompanies: Flow<List<String>> = gate.isBlocked.flatMapLatest { if (it) flowOf(emptyList()) else companyDao.getAllCompanyNames() }

    suspend fun insert(interview: Interview): Long = gate.run {
        interviewDao.insert(interview)
    }

    suspend fun update(interview: Interview) = gate.run {
        interviewDao.update(interview)
    }

    suspend fun delete(interview: Interview) = gate.run {
        interviewDao.delete(interview)
    }

    suspend fun deleteById(id: Long) = gate.run {
        interviewDao.deleteById(id)
    }

    suspend fun getById(id: Long): Interview? {
        return if (gate.isBlocked.value) null else interviewDao.getInterviewById(id)
    }

    // Company methods
    suspend fun findOrCreateCompany(name: String): Company = gate.run {
        // Check if company already exists by name
        companyDao.getCompanyByName(name)?.let { return@run it }

        // Create new local company (no serverId yet)
        val newCompany = Company(name = name)
        val newId = companyDao.insert(newCompany)
        newCompany.copy(id = newId)
    }

    suspend fun updateCompanyServerId(localId: Long, serverId: Int) = gate.run {
        companyDao.getCompanyById(localId)?.let { company ->
            companyDao.update(company.copy(serverId = serverId))
        }
    }

    suspend fun getCompanyByServerId(serverId: Int): Company? {
        return if (gate.isBlocked.value) null else companyDao.getCompanyByServerId(serverId)
    }
}
