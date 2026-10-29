package tools.interviews.android.model

import androidx.annotation.ColorRes
import tools.interviews.android.R

enum class InterviewOutcome(
    val displayName: String,
    @ColorRes val colorRes: Int
) {
    SCHEDULED("Scheduled", R.color.outcome_scheduled),
    AWAITING_RESPONSE("Awaiting Response", R.color.outcome_awaiting),
    PASSED("Passed", R.color.outcome_passed),
    REJECTED("Rejected", R.color.outcome_rejected),
    OFFER_RECEIVED("Offer Received", R.color.outcome_offer_received),
    OFFER_ACCEPTED("Offer Accepted", R.color.outcome_offer_accepted),
    OFFER_DECLINED("Offer Declined", R.color.outcome_offer_declined),
    WITHDREW("Withdrew", R.color.outcome_withdrew)
}
