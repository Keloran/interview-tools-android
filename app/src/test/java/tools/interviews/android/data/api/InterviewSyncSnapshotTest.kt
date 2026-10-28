package tools.interviews.android.data.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InterviewSyncSnapshotTest {
    @Test fun `reads accounts larger than the old 200 row and 2000 row limits`() = runTest {
        for (count in listOf(201, 2001)) {
            val remote = FakeRemote((1..count).map(::row))
            val snapshot = InterviewSyncSnapshotLoader(remote).load(emptySet())
            assertEquals(count, snapshot.interviews.size)
            assertEquals((0 until count step 200).toList(), remote.skips)
            assertTrue(snapshot.deletedIds.isEmpty())
        }
    }

    @Test fun `reads the final empty page when count is an exact page multiple`() = runTest {
        val remote = FakeRemote((1..400).map(::row))
        assertEquals(400, InterviewSyncSnapshotLoader(remote).load(emptySet()).interviews.size)
        assertEquals(listOf(0, 200, 400), remote.skips)
    }

    @Test fun `deduplicates overlapping pages and verifies missing IDs rather than assuming deletion`() = runTest {
        val remote = FakeRemote((1..201).map(::row))
        remote.page = { skip -> if (skip == 0) (1..200).map(::row) else listOf(row(200), row(201)) }
        remote.lookups[202] = row(202) // row shifted between offset pages while the user edited it
        val snapshot = InterviewSyncSnapshotLoader(remote).load(setOf(1, 202, 999))
        assertEquals(202, snapshot.interviews.size)
        assertEquals(setOf(999), snapshot.deletedIds)
        assertEquals(listOf(202, 999), remote.requestedIds)
    }

    @Test fun `failed second page produces no snapshot or deletion plan`() = runTest {
        val remote = FakeRemote((1..201).map(::row))
        remote.page = { skip -> if (skip == 0) (1..200).map(::row) else throw IOException("page failed") }
        assertTrue(runCatching { InterviewSyncSnapshotLoader(remote).load(setOf(999)) }.exceptionOrNull() is IOException)
        assertTrue(remote.requestedIds.isEmpty())
    }

    @Test fun `missing-ID authentication failure cannot be interpreted as a deletion`() = runTest {
        val remote = FakeRemote(emptyList())
        remote.lookupError = IOException("401")
        assertTrue(runCatching { InterviewSyncSnapshotLoader(remote).load(setOf(1)) }.isFailure)
    }

    @Test fun `session changes during page download cancel reconciliation`() = runTest {
        val remote = FakeRemote(listOf(row(1)))
        remote.page = { remote.changedSession = true; listOf(row(1)) }
        assertTrue(runCatching { InterviewSyncSnapshotLoader(remote).load(setOf(999)) }.exceptionOrNull() is CancellationException)
        assertTrue(remote.requestedIds.isEmpty())
    }

    @Test fun `cancellation from a page request propagates unchanged`() = runTest {
        val remote = FakeRemote(emptyList())
        remote.page = { throw CancellationException("cancelled") }
        assertTrue(runCatching { InterviewSyncSnapshotLoader(remote).load(setOf(1)) }.exceptionOrNull() is CancellationException)
    }

    @Test fun `repeating full pages aborts rather than looping forever`() = runTest {
        val remote = FakeRemote((1..200).map(::row))
        remote.page = { (1..200).map(::row) }
        assertTrue(runCatching { InterviewSyncSnapshotLoader(remote).load(setOf(999)) }.isFailure)
        assertEquals(listOf(0, 200), remote.skips)
        assertTrue(remote.requestedIds.isEmpty())
    }

    @Test fun `malformed page or mismatched lookup ID never creates a deletion plan`() = runTest {
        val remote = FakeRemote(listOf(row(0)))
        assertTrue(runCatching { InterviewSyncSnapshotLoader(remote).load(setOf(1)) }.isFailure)
        val mismatch = FakeRemote(emptyList())
        mismatch.lookups[1] = row(2)
        assertTrue(runCatching { InterviewSyncSnapshotLoader(mismatch).load(setOf(1)) }.isFailure)
    }

    private class FakeRemote(val rows: List<APIInterview>) : SyncRemote {
        val skips = mutableListOf<Int>()
        val requestedIds = mutableListOf<Int>()
        val lookups = mutableMapOf<Int, APIInterview>()
        var changedSession = false
        var lookupError: Exception? = null
        var page: ((Int) -> List<APIInterview>)? = null
        override suspend fun companies() = listOf(APICompany(10, "Example"))
        override suspend fun interviews(take: Int, skip: Int): List<APIInterview> {
            skips.add(skip)
            return page?.invoke(skip) ?: rows.drop(skip).take(take)
        }
        override suspend fun interview(id: Int): APIInterview? {
            requestedIds.add(id)
            lookupError?.let { throw it }
            return lookups[id]
        }
        override fun checkSession() {
            if (changedSession) throw CancellationException("account changed")
        }
    }

    companion object {
        fun row(id: Int) = APIInterview(id = id, jobTitle = "Role $id", company = APICompany(10, "Example"),
            applicationDate = "2026-01-01T00:00:00Z", stage = APIStage(1, "Applied"), stageMethod = null,
            date = null, deadline = null, outcome = "AWAITING_RESPONSE", interviewer = null,
            clientCompany = null, notes = null, metadata = null, link = null)
    }
}
