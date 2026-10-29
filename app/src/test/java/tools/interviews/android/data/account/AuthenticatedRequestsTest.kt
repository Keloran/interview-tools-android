package tools.interviews.android.data.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthenticatedRequestsTest {
    @Test fun guestsAndStaleAccountsNeverMakeRequests() = runTest {
        var requests = 0
        val client = AuthenticatedRequests(AccountOperationGate(), { "a" }, {})
        for (owner in listOf(AccountOwners.GUEST, "b")) assertTrue(runCatching { client.run(owner) { requests++ } }.isFailure)
        assertEquals(0, requests)
    }
    @Test fun accountSwitchDuringAuthenticationOrResponseCancelsDelivery() = runTest {
        var owner = "a"
        var requests = 0
        val authSwitch = AuthenticatedRequests(AccountOperationGate(), { owner }, { owner = "b" })
        assertTrue(runCatching { authSwitch.run("a") { requests++ } }.exceptionOrNull() is CancellationException)
        assertEquals(0, requests)
        owner = "a"
        val responseSwitch = AuthenticatedRequests(AccountOperationGate(), { owner }, {})
        assertTrue(runCatching { responseSwitch.run("a") { owner="b"; "private data" } }.exceptionOrNull() is CancellationException)
    }
    @Test fun refreshesCredentialsForEveryRequestAndHonoursDeletionGate() = runTest {
        var credentials = 0
        val gate = AccountOperationGate()
        val client = AuthenticatedRequests(gate, { "a" }, { credentials++ })
        assertEquals(7, client.run("a") { 7 })
        assertEquals(8, client.run("a") { 8 })
        assertEquals(2, credentials)
        gate.closeAndRun {}
        assertTrue(runCatching { client.run("a") { error("Must not request") } }.exceptionOrNull() is CancellationException)
        assertEquals(2, credentials)
    }
}
