package tools.interviews.android.model

val InterviewOutcome.isClosed: Boolean get() = this in setOf(
    InterviewOutcome.PASSED, InterviewOutcome.REJECTED, InterviewOutcome.OFFER_ACCEPTED,
    InterviewOutcome.OFFER_DECLINED, InterviewOutcome.WITHDREW, InterviewOutcome.NO_RESPONSE)
val Interview.stageLabel: String get() = if (stage == InterviewStage.UNKNOWN) serverStageName ?: "Unknown stage" else stage.displayName
val Interview.outcomeLabel: String get() = if (outcome == InterviewOutcome.UNKNOWN) serverOutcomeName ?: "Unknown outcome" else outcome.displayName
val Interview.isUpcomingRecord: Boolean get() = stage != InterviewStage.UNKNOWN && outcome in setOf(
    InterviewOutcome.SCHEDULED, InterviewOutcome.AWAITING_RESPONSE, InterviewOutcome.OFFER_RECEIVED)
fun Interview.stageWireValue(): String? = stage.takeUnless { it == InterviewStage.UNKNOWN }?.displayName
fun Interview.outcomeWireValue(): String? = outcome.takeUnless { it == InterviewOutcome.UNKNOWN }?.name
