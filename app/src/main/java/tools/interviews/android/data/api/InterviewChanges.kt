package tools.interviews.android.data.api

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import tools.interviews.android.model.*

/** Explicit editable fields; identities and newer server fields always come from the current row. */
object InterviewChanges {
    private fun <T> changed(before: T, edited: T, current: T) = if (before != edited) edited else current
    fun apply(current: Interview, before: Interview, edited: Interview): Interview = current.copy(
        jobTitle = changed(before.jobTitle, edited.jobTitle, current.jobTitle),
        companyName = changed(before.companyName, edited.companyName, current.companyName),
        companyId = changed(before.companyId, edited.companyId, current.companyId),
        clientCompany = changed(before.clientCompany, edited.clientCompany, current.clientCompany),
        stage = changed(before.stage, edited.stage, current.stage),
        serverStageId = changed(before.serverStageId, edited.serverStageId, current.serverStageId),
        serverStageName = changed(before.serverStageName, edited.serverStageName, current.serverStageName),
        stageRecognized = changed(before.stageRecognized, edited.stageRecognized, current.stageRecognized),
        stageSelectionChanged = current.stageSelectionChanged || edited.stageSelectionChanged,
        method = changed(before.method, edited.method, current.method),
        serverMethodId = changed(before.serverMethodId, edited.serverMethodId, current.serverMethodId),
        serverMethodName = changed(before.serverMethodName, edited.serverMethodName, current.serverMethodName),
        methodSelectionChanged = current.methodSelectionChanged || edited.methodSelectionChanged,
        outcome = changed(before.outcome, edited.outcome, current.outcome),
        applicationDate = changed(before.applicationDate, edited.applicationDate, current.applicationDate),
        interviewInstant = changed(before.interviewInstant, edited.interviewInstant, current.interviewInstant),
        interviewDate = changed(before.interviewDate, edited.interviewDate, current.interviewDate),
        deadlineInstant = changed(before.deadlineInstant, edited.deadlineInstant, current.deadlineInstant),
        deadline = changed(before.deadline, edited.deadline, current.deadline),
        offerRespondBy = changed(before.offerRespondBy, edited.offerRespondBy, current.offerRespondBy),
        durationMinutes = changed(before.durationMinutes, edited.durationMinutes, current.durationMinutes),
        interviewer = changed(before.interviewer, edited.interviewer, current.interviewer),
        link = changed(before.link, edited.link, current.link),
        jobListing = changed(before.jobListing, edited.jobListing, current.jobListing),
        notes = changed(before.notes, edited.notes, current.notes),
        metadataJSON = changed(before.metadataJSON, edited.metadataJSON, current.metadataJSON)
    )
    fun merge(existing: String?, patch: JsonObject): String? {
        val combined = existing?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
        if (patch.has("stageId")) combined.remove("stageName")
        if (patch.has("stageName")) combined.remove("stageId")
        if (patch.has("stageMethodId")) combined.remove("stageMethodName")
        if (patch.has("stageMethodName")) combined.remove("stageMethodId")
        patch.entrySet().forEach { (key, value) -> combined.add(key, value) }
        return combined.takeIf { it.size() > 0 }?.toString()
    }
    fun afterCreate(row: Interview): JsonObject = JsonObject().apply {
        val initial = when (row.stage) {
            InterviewStage.APPLIED -> InterviewOutcome.AWAITING_RESPONSE
            InterviewStage.OFFER -> InterviewOutcome.OFFER_RECEIVED
            else -> InterviewOutcome.SCHEDULED
        }
        if (row.outcome != initial) row.outcomeWireValue()?.let { addProperty("outcome", it) }
    }
    fun reconcileCreated(row: Interview, server: APIInterview): JsonObject {
        val stage = InterviewCatalog.stageKind(server.stage?.stage)
        val method = InterviewCatalog.methodKind(server.stageMethod?.method)
        val sameStage = row.stage == stage && (stage != InterviewStage.UNKNOWN || row.serverStageId == server.stage?.id)
        val sameMethod = row.serverMethodId?.let { it == server.stageMethod?.id } ?: (row.method == method)
        fun instant(value: String?) = runCatching { InterviewTimes.parseInstant(value) }.getOrNull()
        val baseline = row.copy(jobTitle = server.jobTitle, companyName = server.company.name,
            clientCompany = server.clientCompany, notes = server.notes, link = server.link, interviewer = server.interviewer,
            jobListing = server.metadata?.jobListing,
            outcome = InterviewOutcome.entries.firstOrNull { it.name.equals(server.outcome?.replace(' ', '_'), true) } ?: InterviewOutcome.UNKNOWN,
            stage = stage, serverStageId = if (sameStage) row.serverStageId else server.stage?.id,
            serverStageName = if (sameStage) row.serverStageName else server.stage?.stage,
            method = if (sameMethod) row.method else method,
            serverMethodId = if (sameMethod) row.serverMethodId else server.stageMethod?.id,
            serverMethodName = if (sameMethod) row.serverMethodName else server.stageMethod?.method,
            interviewInstant = if (row.hasMeeting) instant(server.date) else row.interviewInstant,
            deadlineInstant = if (row.stage == InterviewStage.TECHNICAL_TEST) instant(server.deadline) else row.deadlineInstant,
            offerRespondBy = if (row.stage == InterviewStage.OFFER) runCatching { InterviewTimes.parseDay(server.deadline) }.getOrNull() else row.offerRespondBy,
            durationMinutes = row.durationMinutes?.let { server.durationMinutes })
        return diff(baseline, row).apply {
            if (!row.hasMeeting && server.date != null) add("date", JsonNull.INSTANCE)
        }
    }

    fun diff(before: Interview, after: Interview): JsonObject = JsonObject().apply {
        fun nullable(key: String, value: String?) { if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value) }
        if (before.jobTitle != after.jobTitle) addProperty("jobTitle", after.jobTitle)
        if (before.companyName != after.companyName) addProperty("companyName", after.companyName)
        if (before.clientCompany != after.clientCompany) nullable("clientCompany", after.clientCompany)
        if (before.notes != after.notes) addProperty("notes", after.notes ?: "")
        if (before.link != after.link) nullable("link", after.link)
        if (before.interviewer != after.interviewer) nullable("interviewer", after.interviewer)
        if (before.jobListing != after.jobListing) add("metadata", JsonObject().apply {
            if (after.jobListing == null) add("jobListing", JsonNull.INSTANCE) else addProperty("jobListing", after.jobListing)
        })
        if (before.outcome != after.outcome) after.outcomeWireValue()?.let { addProperty("outcome", it) }
        val stageChanged = before.stage != after.stage || before.serverStageId != after.serverStageId || before.serverStageName != after.serverStageName
        if (stageChanged && after.stageSelectionChanged) {
            if (after.serverStageId != null) addProperty("stageId", after.serverStageId)
            else if (after.stage != InterviewStage.UNKNOWN) addProperty("stageName", after.stage.displayName)
            else error("Choose a recognized stage before syncing.")
        }
        if (before.serverMethodId != after.serverMethodId || before.method != after.method || before.serverMethodName != after.serverMethodName) {
            if (after.serverMethodId != null) addProperty("stageMethodId", after.serverMethodId)
            else after.method?.let { addProperty("stageMethodName", when (it) {
                InterviewMethod.PHONE_CALL -> "Phone"
                InterviewMethod.VIDEO_CALL -> "Link"
                InterviewMethod.IN_PERSON -> "In person"
            }) }
        }
        if (stageChanged || before.interviewInstant != after.interviewInstant || before.interviewDate != after.interviewDate) nullable("date", after.meetingWireValue())
        if (stageChanged || before.deadlineInstant != after.deadlineInstant || before.deadline != after.deadline || before.offerRespondBy != after.offerRespondBy) nullable("deadline", after.deadlineWireValue())
        if (before.durationMinutes != after.durationMinutes) {
            val duration = after.durationMinutes ?: 60
            require(duration in 15..480) { "Duration must be 15–480 minutes." }
            addProperty("durationMinutes", duration)
        }
    }
}
