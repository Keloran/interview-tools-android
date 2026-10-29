package tools.interviews.android.data.api

import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewStage

data class InterviewCatalog(val stages: List<APIStage>, val methods: List<APIStageMethod>) {
    companion object {
        fun fallback() = InterviewCatalog(
            InterviewStage.entries.filter { it != InterviewStage.UNKNOWN }.map { APIStage(0, it.displayName) },
            InterviewMethod.entries.map { APIStageMethod(0, it.displayName) })
        fun stageKind(name: String?) = InterviewStage.entries.firstOrNull { it != InterviewStage.UNKNOWN && it.displayName.equals(name?.trim(), true) } ?: InterviewStage.UNKNOWN
        fun methodKind(name: String?): InterviewMethod? = when (name?.trim()?.lowercase()) {
            "video call", "video", "link", "zoom", "zoomgov", "teams", "google meet", "webex", "skype", "whereby", "jitsi", "facetime", "discord", "slack", "bluejeans", "gotomeeting", "amazon chime", "8x8" -> InterviewMethod.VIDEO_CALL
            "phone call", "phone" -> InterviewMethod.PHONE_CALL
            "in person", "in_person", "onsite" -> InterviewMethod.IN_PERSON
            else -> null
        }
    }
}

interface SyncWrites {
    suspend fun applicationIdForInterview(id: Int): Int? = null
    suspend fun updateRecruiter(id: Int, body: com.google.gson.JsonObject) { error("Recruiter updates are unavailable.") }
    suspend fun verifyCreateSupport() {}
    suspend fun patchInterview(id: Int, patch: com.google.gson.JsonObject): APIInterview = error("Patch updates are unavailable.")
    suspend fun createInterview(request: CreateInterviewRequest): APIInterview
    suspend fun updateInterview(id: Int, request: UpdateInterviewRequest): APIInterview
}
