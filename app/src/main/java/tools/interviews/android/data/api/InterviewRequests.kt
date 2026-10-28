package tools.interviews.android.data.api

import tools.interviews.android.model.*

object InterviewRequests {
    fun update(interview: Interview) = UpdateInterviewRequest(
        outcome = interview.outcomeWireValue(),
        stage = interview.stageWireValue(),
        date = interview.meetingWireValue(),
        deadline = interview.deadlineWireValue(),
        interviewer = interview.interviewer,
        notes = interview.notes,
        link = interview.link
    )
}
