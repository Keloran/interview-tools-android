package tools.interviews.android.data

import kotlinx.coroutines.flow.Flow
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview

class InterviewRepository(
    private val interviewDao: InterviewDao,
    private val companyDao: CompanyDao
) {
    val allInterviews: Flow<List<Interview>> = interviewDao.getAllInterviews()
    val allCompanies: Flow<List<String>> = companyDao.getAllCompanyNames()

    suspend fun insert(interview: Interview): Long {
        return interviewDao.insert(interview)
    }

    suspend fun update(interview: Interview) {
        interviewDao.update(interview)
    }

    suspend fun delete(interview: Interview) {
        interviewDao.delete(interview)
    }

    suspend fun deleteById(id: Long) {
        interviewDao.deleteById(id)
    }

    suspend fun getById(id: Long): Interview? {
        return interviewDao.getInterviewById(id)
    }

    // Company methods
    suspend fun findOrCreateCompany(name: String): Company {
        // Check if company already exists by name
        companyDao.getCompanyByName(name)?.let { return it }

        // Create new local company (no serverId yet)
        val newCompany = Company(name = name)
        val newId = companyDao.insert(newCompany)
        return newCompany.copy(id = newId)
    }

    suspend fun updateCompanyServerId(localId: Long, serverId: Int) {
        companyDao.getCompanyById(localId)?.let { company ->
            companyDao.update(company.copy(serverId = serverId))
        }
    }

    suspend fun getCompanyByServerId(serverId: Int): Company? {
        return companyDao.getCompanyByServerId(serverId)
    }
}
