package tools.interviews.android.data.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSessionControllerTest {
    @Test fun `signout hides ownership and clears credentials before SDK network request completes`() = runTest {
        val gate = AccountOperationGate()
        var user: String? = "account-a"
        var tokenCleared = false
        val subject = AccountSessionController(gate, { tokenCleared = true }, {}, { false })
        subject.changed(user)
        tokenCleared = false
        val finished = CompletableDeferred<Unit>()
        val job = launch { subject.signOut({ user }) { finished.await(); user = null } }
        runCurrent()
        assertTrue(tokenCleared)
        assertTrue(gate.isBlocked.value)
        assertEquals(AccountOwners.GUEST, subject.owner.value)
        assertEquals(AccountOwners.GUEST, subject.effectiveOwner("account-a"))
        finished.complete(Unit)
        job.join()
        assertFalse(gate.isBlocked.value)
    }
    @Test fun `direct account switch resets the previous sync session`() = runTest {
        val gate = AccountOperationGate()
        var resets = 0
        val subject = AccountSessionController(gate, {}, { resets++ }, { false })
        subject.changed("account-a")
        subject.changed("account-b")
        assertEquals("account-b", subject.owner.value)
        assertEquals(2, resets)
        subject.changed(null)
        assertEquals(AccountOwners.GUEST, subject.owner.value)
        assertEquals(3, resets)
    }
    @Test fun `an outstanding deletion cleanup cannot be reopened by account switching`() = runTest {
        val gate = AccountOperationGate(initiallyClosed = true)
        val subject = AccountSessionController(gate, {}, {}, { true })
        subject.changed("account-b")
        assertTrue(gate.isBlocked.value)
        assertEquals("account-b", subject.owner.value)
    }
}
