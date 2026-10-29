package tools.interviews.android.data.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class AccountOperationGateTest {
    @Test fun `deletion waits for active sync and rejects queued stale writes`() = runTest {
        val gate = AccountOperationGate()
        val releaseSync = CompletableDeferred<Unit>()
        var cleaned = false
        var staleWrite = false
        val sync = async { gate.run { releaseSync.await() } }
        runCurrent()
        val queued = async { runCatching { gate.run { staleWrite = true } } }
        runCurrent()
        val deletion = async { gate.closeAndRun { cleaned = true; gate.reopen() } }
        runCurrent()
        assertTrue(gate.isBlocked.value)
        assertFalse(cleaned)
        releaseSync.complete(Unit)
        sync.await()
        deletion.await()
        assertTrue(queued.await().isFailure)
        assertTrue(cleaned)
        assertFalse(staleWrite)
        assertFalse(gate.isBlocked.value)
    }

    @Test fun `pending cleanup after restart blocks writes until explicitly reopened`() = runTest {
        val gate = AccountOperationGate(initiallyClosed = true)
        assertTrue(runCatching { gate.run { fail("must not write") } }.isFailure)
        gate.closeAndRun { gate.reopen() }
        assertEquals("new guest entry", gate.run { "new guest entry" })
    }
}
