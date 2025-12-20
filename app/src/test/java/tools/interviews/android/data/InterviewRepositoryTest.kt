package tools.interviews.android.data

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate

class InterviewRepositoryTest {

    private lateinit var repository: InterviewRepository
    private lateinit var mockInterviewDao: InterviewDao
    private lateinit var mockCompanyDao: CompanyDao

    @Before
    fun setup() {
        mockInterviewDao = mockk(relaxed = true)
        mockCompanyDao = mockk(relaxed = true)
        repository = InterviewRepository(mockInterviewDao, mockCompanyDao)
    }

    // ==================== Insert Tests ====================

    @Test
    fun `insert calls dao insert and returns id`() = runTest {
        val interview = createTestInterview()
        coEvery { mockInterviewDao.insert(interview) } returns 42L

        val result = repository.insert(interview)

        assertEquals(42L, result)
        coVerify(exactly = 1) { mockInterviewDao.insert(interview) }
    }

    @Test
    fun `insert with zero id creates new record`() = runTest {
        val interview = createTestInterview(id = 0)
        coEvery { mockInterviewDao.insert(interview) } returns 1L

        val result = repository.insert(interview)

        assertEquals(1L, result)
        coVerify { mockInterviewDao.insert(match { it.id == 0L }) }
    }

    // ==================== Update Tests ====================

    @Test
    fun `update calls dao update`() = runTest {
        val interview = createTestInterview(id = 1)

        repository.update(interview)

        coVerify(exactly = 1) { mockInterviewDao.update(interview) }
    }

    @Test
    fun `update with modified fields passes correct data`() = runTest {
        val interview = createTestInterview(id = 1).copy(
            outcome = InterviewOutcome.PASSED,
            stage = InterviewStage.SECOND_STAGE
        )

        repository.update(interview)

        coVerify {
            mockInterviewDao.update(match {
                it.outcome == InterviewOutcome.PASSED &&
                it.stage == InterviewStage.SECOND_STAGE
            })
        }
    }

    // ==================== Delete Tests ====================

    @Test
    fun `delete calls dao delete`() = runTest {
        val interview = createTestInterview(id = 1)

        repository.delete(interview)

        coVerify(exactly = 1) { mockInterviewDao.delete(interview) }
    }

    @Test
    fun `deleteById calls dao deleteById`() = runTest {
        repository.deleteById(42L)

        coVerify(exactly = 1) { mockInterviewDao.deleteById(42L) }
    }

    @Test
    fun `deleteById with non-existent id still calls dao`() = runTest {
        // DAO should handle non-existent ids gracefully
        repository.deleteById(999999L)

        coVerify(exactly = 1) { mockInterviewDao.deleteById(999999L) }
    }

    // ==================== GetById Tests ====================

    @Test
    fun `getById returns interview when found`() = runTest {
        val expected = createTestInterview(id = 1)
        coEvery { mockInterviewDao.getInterviewById(1) } returns expected

        val result = repository.getById(1)

        assertEquals(expected, result)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { mockInterviewDao.getInterviewById(999) } returns null

        val result = repository.getById(999)

        assertNull(result)
    }

    @Test
    fun `getById with zero id returns null`() = runTest {
        coEvery { mockInterviewDao.getInterviewById(0) } returns null

        val result = repository.getById(0)

        assertNull(result)
    }

    @Test
    fun `getById with negative id returns null`() = runTest {
        coEvery { mockInterviewDao.getInterviewById(-1) } returns null

        val result = repository.getById(-1)

        assertNull(result)
    }

    // ==================== AllInterviews Flow Tests ====================

    @Test
    fun `allInterviews emits list from dao`() = runTest {
        val interviews = listOf(
            createTestInterview(id = 1),
            createTestInterview(id = 2)
        )
        every { mockInterviewDao.getAllInterviews() } returns flowOf(interviews)

        // Re-create repository to use new mock
        repository = InterviewRepository(mockInterviewDao, mockCompanyDao)

        repository.allInterviews.test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `allInterviews emits empty list when no interviews`() = runTest {
        every { mockInterviewDao.getAllInterviews() } returns flowOf(emptyList())
        repository = InterviewRepository(mockInterviewDao, mockCompanyDao)

        repository.allInterviews.test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== FindOrCreateCompany Tests ====================

    @Test
    fun `findOrCreateCompany returns existing company`() = runTest {
        val existingCompany = Company(id = 1, name = "Acme Corp", serverId = 100)
        coEvery { mockCompanyDao.getCompanyByName("Acme Corp") } returns existingCompany

        val result = repository.findOrCreateCompany("Acme Corp")

        assertEquals(existingCompany, result)
        coVerify(exactly = 0) { mockCompanyDao.insert(any()) }
    }

    @Test
    fun `findOrCreateCompany creates new company when not found`() = runTest {
        coEvery { mockCompanyDao.getCompanyByName("New Corp") } returns null
        coEvery { mockCompanyDao.insert(any()) } returns 42L

        val result = repository.findOrCreateCompany("New Corp")

        assertEquals("New Corp", result.name)
        assertEquals(42L, result.id)
        assertNull(result.serverId)
        coVerify { mockCompanyDao.insert(match { it.name == "New Corp" }) }
    }

    @Test
    fun `findOrCreateCompany handles empty company name`() = runTest {
        coEvery { mockCompanyDao.getCompanyByName("") } returns null
        coEvery { mockCompanyDao.insert(any()) } returns 1L

        val result = repository.findOrCreateCompany("")

        assertEquals("", result.name)
        coVerify { mockCompanyDao.insert(match { it.name == "" }) }
    }

    @Test
    fun `findOrCreateCompany handles company name with special characters`() = runTest {
        val specialName = "Acme & Co. (UK) Ltd."
        coEvery { mockCompanyDao.getCompanyByName(specialName) } returns null
        coEvery { mockCompanyDao.insert(any()) } returns 1L

        val result = repository.findOrCreateCompany(specialName)

        assertEquals(specialName, result.name)
    }

    @Test
    fun `findOrCreateCompany is case sensitive`() = runTest {
        val existingCompany = Company(id = 1, name = "Acme Corp")
        coEvery { mockCompanyDao.getCompanyByName("Acme Corp") } returns existingCompany
        coEvery { mockCompanyDao.getCompanyByName("acme corp") } returns null
        coEvery { mockCompanyDao.insert(any()) } returns 2L

        val result1 = repository.findOrCreateCompany("Acme Corp")
        val result2 = repository.findOrCreateCompany("acme corp")

        assertEquals(1L, result1.id)
        assertEquals(2L, result2.id)
    }

    // ==================== UpdateCompanyServerId Tests ====================

    @Test
    fun `updateCompanyServerId updates existing company`() = runTest {
        val company = Company(id = 1, name = "Acme Corp", serverId = null)
        coEvery { mockCompanyDao.getCompanyById(1) } returns company

        repository.updateCompanyServerId(1, 100)

        coVerify {
            mockCompanyDao.update(match {
                it.id == 1L && it.serverId == 100
            })
        }
    }

    @Test
    fun `updateCompanyServerId does nothing when company not found`() = runTest {
        coEvery { mockCompanyDao.getCompanyById(999) } returns null

        repository.updateCompanyServerId(999, 100)

        coVerify(exactly = 0) { mockCompanyDao.update(any()) }
    }

    @Test
    fun `updateCompanyServerId overwrites existing serverId`() = runTest {
        val company = Company(id = 1, name = "Acme Corp", serverId = 50)
        coEvery { mockCompanyDao.getCompanyById(1) } returns company

        repository.updateCompanyServerId(1, 100)

        coVerify {
            mockCompanyDao.update(match {
                it.serverId == 100
            })
        }
    }

    // ==================== GetCompanyByServerId Tests ====================

    @Test
    fun `getCompanyByServerId returns company when found`() = runTest {
        val expected = Company(id = 1, name = "Acme Corp", serverId = 100)
        coEvery { mockCompanyDao.getCompanyByServerId(100) } returns expected

        val result = repository.getCompanyByServerId(100)

        assertEquals(expected, result)
    }

    @Test
    fun `getCompanyByServerId returns null when not found`() = runTest {
        coEvery { mockCompanyDao.getCompanyByServerId(999) } returns null

        val result = repository.getCompanyByServerId(999)

        assertNull(result)
    }

    // ==================== AllCompanies Flow Tests ====================

    @Test
    fun `allCompanies emits company names from dao`() = runTest {
        val companyNames = listOf("Acme Corp", "Beta Inc", "Gamma Ltd")
        every { mockCompanyDao.getAllCompanyNames() } returns flowOf(companyNames)
        repository = InterviewRepository(mockInterviewDao, mockCompanyDao)

        repository.allCompanies.test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertTrue(result.contains("Acme Corp"))
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `allCompanies emits empty list when no companies`() = runTest {
        every { mockCompanyDao.getAllCompanyNames() } returns flowOf(emptyList())
        repository = InterviewRepository(mockInterviewDao, mockCompanyDao)

        repository.allCompanies.test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== Helper Functions ====================

    private fun createTestInterview(
        id: Long = 0,
        jobTitle: String = "Software Engineer",
        companyName: String = "Test Company"
    ) = Interview(
        id = id,
        serverId = null,
        companyId = null,
        jobTitle = jobTitle,
        companyName = companyName,
        clientCompany = null,
        stage = InterviewStage.APPLIED,
        method = null,
        outcome = InterviewOutcome.SCHEDULED,
        applicationDate = LocalDate.of(2024, 6, 15),
        interviewDate = null,
        deadline = null,
        interviewer = null,
        link = null,
        jobListing = null,
        notes = null,
        metadataJSON = null
    )
}
