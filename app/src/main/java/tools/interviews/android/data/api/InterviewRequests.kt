package tools.interviews.android.data.api

import tools.interviews.android.model.*

object InterviewRequests {
    fun create(interview: Interview, previousServerId: Int? = null): CreateInterviewRequest {
        require(interview.stage != InterviewStage.UNKNOWN || (interview.stageRecognized && interview.serverStageId != null)) { "Choose a recognized stage before uploading." }
        require(interview.outcome != InterviewOutcome.UNKNOWN) { "Choose a recognized outcome before uploading." }
        require(interview.previousLocalId == null || previousServerId != null) { "The previous stage must sync first." }
        val parent = previousServerId ?: interview.parentId
        require(parent == null || parent > 0) { "The previous stage must have a valid server ID." }
        interview.durationMinutes?.let { require(it in 15..480) { "Duration must be 15–480 minutes." } }
        return CreateInterviewRequest(
            stage = if (interview.stage == InterviewStage.UNKNOWN) requireNotNull(interview.serverStageName) else interview.stage.displayName,
            companyName = interview.companyName, clientCompany = interview.clientCompany, jobTitle = interview.jobTitle,
            jobPostingLink = interview.jobListing, date = interview.meetingWireValue(), deadline = interview.deadlineWireValue(),
            interviewer = interview.interviewer,
            locationType = when (interview.method) {
                InterviewMethod.PHONE_CALL -> "phone"
                InterviewMethod.IN_PERSON -> "in_person"
                else -> "link"
            },
            interviewLink = interview.link, notes = interview.notes,
            offerDeadline = if (interview.stage == InterviewStage.OFFER) interview.offerRespondBy?.toString() else null,
            previousInterviewId = parent,
            durationMinutes = interview.durationMinutes, stageMethodId = interview.serverMethodId, clientMutationId = interview.clientMutationId
        )
    }

    fun update(interview: Interview) = UpdateInterviewRequest(
        outcome = interview.outcomeWireValue(),
        stage = interview.stageWireValue(),
        date = interview.meetingWireValue(),
        deadline = interview.deadlineWireValue(),
        interviewer = interview.interviewer,
        notes = interview.notes,
        link = interview.link,
        stageId = interview.serverStageId.takeIf { interview.stageSelectionChanged },
        stageMethodId = interview.serverMethodId.takeIf { interview.methodSelectionChanged },
        durationMinutes = interview.durationMinutes
    )
}
