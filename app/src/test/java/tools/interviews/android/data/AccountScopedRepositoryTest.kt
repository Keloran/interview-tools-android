package tools.interviews.android.data

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate

class AccountScopedRepositoryTest {
    private val interviews = mockk<InterviewDao>(relaxed = true)
    private val companies = mockk<CompanyDao>(relaxed = true)
    private val owner = MutableStateFlow("account-a")
    private fun repository() = InterviewRepository(interviews, companies, owner = owner)
    private fun row(selected: String, id: Long = 1) = Interview(id = id, ownerId = selected,
        companyName = "Example", jobTitle = "$selected private role", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1))

    @Test fun `switching accounts clears old rows before exposing the new account`() = runTest {
        val a = MutableStateFlow(listOf(row("account-a")))
        val b = MutableStateFlow(listOf(row("account-b", 2)))
        every { interviews.getAllInterviews("account-a") } returns a
        every { interviews.getAllInterviews("account-b") } returns b
        repository().allInterviews.test {
            assertTrue(awaitItem().isEmpty())
            assertEquals("account-a", awaitItem().single().ownerId)
            owner.value = "account-b"
            assertTrue(awaitItem().isEmpty())
            assertEquals("account-b", awaitItem().single().ownerId)
            a.value = listOf(row("account-a", 3)) // late emission from the previous account
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `signout switches to guest data without exposing either account`() = runTest {
        every { interviews.getAllInterviews("account-a") } returns MutableStateFlow(listOf(row("account-a")))
        every { interviews.getAllInterviews(AccountOwners.GUEST) } returns MutableStateFlow(listOf(row(AccountOwners.GUEST, 2)))
        repository().allInterviews.test {
            awaitItem(); awaitItem()
            owner.value = AccountOwners.GUEST
            assertTrue(awaitItem().isEmpty())
            assertEquals(AccountOwners.GUEST, awaitItem().single().ownerId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `stale screen cannot update or delete another account after switching`() = runTest {
        owner.value = "account-b"
        val repo = repository()
        assertTrue(runCatching { repo.update(row("account-a")) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { repo.delete(row("account-a")) }.exceptionOrNull() is CancellationException)
        coVerify(exactly = 0) { interviews.update(any()) }
        coVerify(exactly = 0) { interviews.delete(any()) }
    }

    @Test fun `reads and pending creates stay scoped to their owner`() = runTest {
        coEvery { interviews.getInterviewById(1, "account-a") } returns null
        assertNull(repository().getById(1))
        assertTrue(runCatching { repository().insert(row("account-b").copy(id = 0)) }.isFailure)
        coVerify(exactly = 0) { interviews.insert(any()) }
        coVerify(exactly = 0) { interviews.getInterviewById(1, "account-b") }
    }

    @Test fun `cross-account company relationships are rejected`() = runTest {
        coEvery { companies.getCompanyById(2, "account-a") } returns null
        val candidate = row("account-a").copy(id = 0, companyId = 2)
        assertTrue(runCatching { repository().insert(candidate) }.isFailure)
        coVerify(exactly = 0) { interviews.insert(any()) }
    }

    @Test fun `guest records move to an account only through explicit import`() = runTest {
        val guest = row(AccountOwners.GUEST).copy(serverId = null)
        val legacy = row(AccountOwners.LEGACY, 2).copy(serverId = null)
        coEvery { interviews.localOnly(AccountOwners.GUEST) } returns listOf(guest)
        coEvery { interviews.localOnly(AccountOwners.LEGACY) } returns listOf(legacy)
        coEvery { companies.getCompanyByName("Example", "account-a") } returns Company(id = 5, name = "Example", ownerId = "account-a")
        val repo = repository()
        coVerify(exactly = 0) { interviews.update(any()) }
        repo.importLocal("account-a")
        coVerify(exactly = 2) { interviews.update(match { it.ownerId == "account-a" && it.serverId == null && it.companyId == 5L }) }
        coVerify(exactly = 0) { interviews.localOnly("account-b") }
    }

    @Test fun `guest mode cannot initiate an account import`() = runTest {
        owner.value = AccountOwners.GUEST
        assertTrue(runCatching { repository().importLocal(AccountOwners.GUEST) }.isFailure)
        coVerify(exactly = 0) { interviews.localOnly(any()) }
    }

    @Test fun `legacy restore remains local and does not include downloaded rows`() = runTest {
        owner.value = AccountOwners.GUEST
        coEvery { interviews.localOnly(AccountOwners.LEGACY) } returns listOf(row(AccountOwners.LEGACY).copy(serverId = null))
        coEvery { companies.getCompanyByName("Example", AccountOwners.GUEST) } returns Company(id = 5, name = "Example")
        repository().importLocal(AccountOwners.GUEST, restoreLegacyOnly = true)
        coVerify { interviews.update(match { it.ownerId == AccountOwners.GUEST && it.serverId == null }) }
        coVerify(exactly = 0) { interviews.localOnly(AccountOwners.GUEST) }
    }
}
