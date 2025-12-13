package tools.interviews.android.data

import kotlinx.coroutines.flow.Flow
import tools.interviews.android.model.Interview

class InterviewRepository(private val interviewDao: InterviewDao) {
    val allInterviews: Flow<List<Interview>> = interviewDao.getAllInterviews()
    val allCompanies: Flow<List<String>> = interviewDao.getAllCompanies()

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
}
