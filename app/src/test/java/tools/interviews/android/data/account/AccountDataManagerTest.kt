package tools.interviews.android.data.account

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDataManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val api = mockk<AccountDataApi>()
    private var account: String? = "account-a"
    private var clears = mutableListOf<Boolean>()
    private var signedOut = 0
    private var clearedToken = 0
    private var failCleanup = false
    private var failSignOut = false
    private val checkpoint = MemoryCheckpoint()
    private val gate = AccountOperationGate()

    private fun manager() = AccountDataManager(api, { account }, { "fresh-token" }, checkpoint, gate, { _, removeGuests ->
        if (failCleanup) throw IOException("Local storage unavailable")
        clears.add(removeGuests)
    }, {
        if (failSignOut) throw IOException("Sign out failed")
        signedOut++
        account = null
    }, { clearedToken++ }, File(temporary.root, "exports"))

    @Test fun `confirmation is required before calling API or clearing anything`() = runTest {
        assertTrue(runCatching { manager().delete("yes", false) }.isFailure)
        coVerify(exactly = 0) { api.deleteAccount(any(), any()) }
        assertTrue(clears.isEmpty())
        assertEquals(0, signedOut)
    }

    @Test fun `successful deletion clears account cache and signs out after remote acknowledgement`() = runTest {
        coEvery { api.deleteAccount("fresh-token", "delete my account") } coAnswers {
            assertTrue(clears.isEmpty())
            assertEquals(0, signedOut)
            Unit
        }
        val exports = File(File(temporary.root, "exports"), AccountOwners.exportFolder("account-a")).apply { mkdirs() }
        File(exports, "private.csv").writeText("private notes")
        val subject = manager()
        subject.delete("delete my account", false)
        assertEquals(listOf(false), clears)
        assertEquals(1, signedOut)
        assertEquals(1, clearedToken)
        assertNull(checkpoint.value)
        assertFalse(subject.pendingCleanup)
        assertFalse(gate.isBlocked.value)
        assertFalse(exports.exists())
    }

    @Test fun `explicit guest removal is passed to cleanup only after success`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } returns Unit
        manager().delete(" DELETE MY ACCOUNT ", true)
        assertEquals(listOf(true), clears)
    }

    @Test fun `partial server or network failure preserves local data and permits retry`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } throws IOException("Data deleted, login removal failed; retry")
        val subject = manager()
        val error = runCatching { subject.delete("delete my account", false) }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("login removal failed"))
        assertTrue(clears.isEmpty())
        assertEquals(0, signedOut)
        assertNull(checkpoint.value)
        assertFalse(gate.isBlocked.value)
        coEvery { api.deleteAccount(any(), any()) } returns Unit
        subject.delete("delete my account", false)
        assertEquals(1, signedOut)
    }

    @Test fun `cleanup failure is checkpointed and retry after restart does not delete remotely again`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } returns Unit
        failCleanup = true
        assertTrue(runCatching { manager().delete("delete my account", true) }.isFailure)
        assertEquals(PendingDeletion("account-a", true), checkpoint.value)
        assertTrue(gate.isBlocked.value)
        failCleanup = false
        // Simulate an already-expired/deleted Clerk session after restarting the app.
        account = null
        val resumed = manager()
        resumed.delete("delete my account", false)
        coVerify(exactly = 1) { api.deleteAccount(any(), any()) }
        assertEquals(listOf(true), clears) // original guest choice is retained
        assertFalse(resumed.pendingCleanup)
        assertFalse(gate.isBlocked.value)
    }

    @Test fun `signout failure keeps retry state without requesting another deletion`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } returns Unit
        failSignOut = true
        val subject = manager()
        assertTrue(runCatching { subject.delete("delete my account", false) }.isFailure)
        assertTrue(subject.pendingCleanup)
        assertTrue(gate.isBlocked.value)
        failSignOut = false
        subject.delete("delete my account", false)
        coVerify(exactly = 1) { api.deleteAccount(any(), any()) }
        assertFalse(subject.pendingCleanup)
        assertEquals(1, signedOut)
    }

    @Test fun `different account is never signed out after the original deletion response`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } coAnswers { account = "account-b"; Unit }
        manager().delete("delete my account", false)
        assertEquals("account-b", account)
        assertEquals(0, signedOut)
    }

    @Test fun `checkpoint persistence failure can be retried in the same process`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } returns Unit
        checkpoint.failSave = true
        val subject = manager()
        assertTrue(runCatching { subject.delete("delete my account", false) }.isFailure)
        assertTrue(subject.pendingCleanup)
        checkpoint.failSave = false
        subject.delete("delete my account", false)
        coVerify(exactly = 1) { api.deleteAccount(any(), any()) }
        assertFalse(subject.pendingCleanup)
    }

    @Test fun `export uses current credentials and discards a result after account switch`() = runTest {
        val directory = File(File(temporary.root, "exports"), AccountOwners.exportFolder("account-a")).apply { mkdirs() }
        val file = File(directory, "stories.csv").apply { writeText("notes") }
        coEvery { api.export("fresh-token", ExportKind.STORIES, directory) } coAnswers { account = "account-b"; file }
        assertTrue(runCatching { manager().export(ExportKind.STORIES) }.isFailure)
        assertFalse(file.exists())
    }

    @Test fun `signed out user cannot export or initiate deletion`() = runTest {
        account = null
        assertTrue(runCatching { manager().export(ExportKind.APPLICATIONS) }.isFailure)
        assertTrue(runCatching { manager().delete("delete my account", false) }.isFailure)
        coVerify(exactly = 0) { api.export(any(), any(), any()) }
        coVerify(exactly = 0) { api.deleteAccount(any(), any()) }
    }

    @Test fun `activity cancellation cannot interrupt confirmed deletion cleanup`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        coEvery { api.deleteAccount(any(), any()) } coAnswers { requestStarted.complete(Unit); response.await() }
        val deletion = launch { manager().delete("delete my account", false) }
        requestStarted.await()
        deletion.cancel() // Activity destroyed while its confirmed request is in flight.
        response.complete(Unit)
        deletion.join()
        assertEquals(listOf(false), clears)
        assertEquals(1, signedOut)
        assertNull(checkpoint.value)
        assertFalse(gate.isBlocked.value)
    }

    @Test fun `deletion preserves exports from another account`() = runTest {
        coEvery { api.deleteAccount(any(), any()) } returns Unit
        val other = File(File(File(temporary.root, "exports"), AccountOwners.exportFolder("account-b")), "other.csv")
        other.parentFile!!.mkdirs()
        other.writeText("Other account notes")
        manager().delete("delete my account", false)
        assertEquals("Other account notes", other.readText())
    }

    @Test fun `confirmation stays bound to the original account if the session switches`() = runTest {
        account = "account-b"
        assertTrue(runCatching { manager().delete("delete my account", false, "account-a") }.isFailure)
        coVerify(exactly = 0) { api.deleteAccount(any(), any()) }
        assertTrue(clears.isEmpty())
    }

    private class MemoryCheckpoint : DeletionCheckpoint {
        var value: PendingDeletion? = null
        var failSave = false
        override fun read() = value
        override fun save(value: PendingDeletion) { if (failSave) throw IOException("Storage full"); this.value = value }
        override fun clear() { value = null }
    }
}
