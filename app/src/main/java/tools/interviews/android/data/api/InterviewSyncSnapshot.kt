package tools.interviews.android.data.api

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface SyncRemote {
    suspend fun stages(): List<APIStage> = emptyList()
    suspend fun companies(): List<APICompany>
    suspend fun interviews(take: Int, skip: Int): List<APIInterview>
    /** Null means an authoritative 404, never a transport/decoding/authentication failure. */
    suspend fun interview(id: Int): APIInterview?
    fun checkSession()
}

data class InterviewSyncSnapshot(
    val companies: List<APICompany>,
    val interviews: List<APIInterview>,
    val deletedIds: Set<Int>
)

/** Offset pages are not deletion authority: verify each missing cached ID individually. */
class InterviewSyncSnapshotLoader(private val remote: SyncRemote, private val pageSize: Int = 200) {
    suspend fun load(cachedIds: Set<Int>): InterviewSyncSnapshot {
        remote.checkSession()
        val companies = remote.companies()
        remote.checkSession()
        val records = linkedMapOf<Int, APIInterview>()
        var skip = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            remote.checkSession()
            val page = remote.interviews(pageSize, skip)
            remote.checkSession()
            if (page.size > pageSize || page.any { it.id <= 0 }) throw IOException("Invalid sync page. Existing history was kept.")
            val before = records.size
            page.forEach { records[it.id] = it }
            if (page.size < pageSize) break
            if (records.size == before || skip >= 100_000) throw IOException("Sync pages did not finish. Existing history was kept.")
            skip += page.size
        }
        val deleted = mutableSetOf<Int>()
        for (id in cachedIds - records.keys) {
            currentCoroutineContext().ensureActive()
            remote.checkSession()
            val found = remote.interview(id)
            remote.checkSession()
            if (found == null) deleted.add(id)
            else {
                if (found.id != id) throw IOException("The server returned a different record. Existing history was kept.")
                records[id] = found
            }
        }
        currentCoroutineContext().ensureActive()
        remote.checkSession()
        return InterviewSyncSnapshot(companies, records.values.toList(), deleted)
    }
}
