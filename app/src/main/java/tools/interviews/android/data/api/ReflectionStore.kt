package tools.interviews.android.data.api

import com.google.gson.*
import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.InterviewDao
import tools.interviews.android.data.account.AccountOperationGate

interface ReflectionRemote {
    suspend fun reflectionInterview(id: Int): APIInterview

    suspend fun prep(id: Int): JsonObject
}

class ReflectionStore(
    private val dao: InterviewDao,
    private val gate: AccountOperationGate,
    private val remote: ReflectionRemote,
    private val ownerNow: () -> String,
    private val authenticate: suspend (String) -> Unit,
) {
    private fun check(owner: String) {
        if (ownerNow() != owner) throw CancellationException("Account changed.")
    }

    suspend fun refresh(owner: String, id: Long) = gate.run {
        check(owner)
        val row = dao.getInterviewById(id, owner) ?: error("Interview not found.")
        val serverId = row.serverId ?: return@run
        authenticate(owner)
        check(owner)
        val response = remote.reflectionInterview(serverId)
        check(owner)
        require(response.id == serverId) { "Unexpected interview response." }
        var latest = dao.getInterviewById(id, owner) ?: error("Interview not found.")
        val pending = latest.pendingChanges?.let { JsonParser.parseString(it).asJsonObject }
        latest =
            latest.copy(
                debriefJSON =
                    if (pending?.has("debrief") == true) latest.debriefJSON
                    else StageReflection.debriefFromApi(response),
                debriefedAt =
                    if (pending?.has("debrief") == true) latest.debriefedAt
                    else response.debriefedAt,
                feedbackJSON =
                    if (pending?.has("feedback") == true) latest.feedbackJSON
                    else StageReflection.feedbackFromApi(response),
            )
        dao.update(latest)
        authenticate(owner)
        check(owner)
        val prep = remote.prep(serverId)
        check(owner)
        require(prep.has("notes") && prep.has("storyIds")) { "Preparation was empty." }
        latest = dao.getInterviewById(id, owner) ?: error("Interview not found.")
        if (latest.pendingPrepJSON == null) dao.update(latest.copy(prepJSON = prep.toString()))
    }
}
