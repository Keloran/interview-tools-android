package tools.interviews.android.data.api

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class MessageTemplatesTest {
    @Test
    fun recordedOrPendingFollowUpsDoNotProduceDuplicateNotifications() {
        val due = "2026-01-01T00:00:00Z"
        val pending =
            com.google.gson.JsonObject().apply {
                addProperty("pending", true)
                addProperty("dueAt", due)
            }
        assertFalse(
            tools.interviews.android.reminders.FollowUpNotificationPolicy.shouldNotify(
                due,
                pending,
                null,
            )
        )
        pending.addProperty("pending", false)
        assertFalse(
            tools.interviews.android.reminders.FollowUpNotificationPolicy.shouldNotify(
                due,
                pending,
                null,
            )
        )
        assertFalse(
            tools.interviews.android.reminders.FollowUpNotificationPolicy.shouldNotify(
                due,
                null,
                due,
            )
        )
        assertTrue(
            tools.interviews.android.reminders.FollowUpNotificationPolicy.shouldNotify(
                "2026-01-09T00:00:00Z",
                pending,
                due,
            )
        )
    }

    @Test
    fun applicationChasersDoNotClaimAnInterviewHappened() {
        val context = MessageContext("Engineer", "Agency", "Employer", "Applied", null, null)
        val templates = MessageTemplates.available("AWAITING_RESPONSE", "Applied", null)
        assertFalse(templates.contains(MessageTemplate.THANK_YOU))
        val message = MessageTemplates.render(MessageTemplate.FOLLOW_UP, context)
        assertTrue(message.contains("my application for the Engineer role with Employer"))
        assertFalse(message.contains("my interview"))
        assertTrue(message.startsWith("Hi,"))
    }

    @Test
    fun postInterviewThanksUseKnownNamesAndLocalMeetingDay() {
        val date = Instant.parse("2026-06-01T23:30:00Z")
        val context =
            MessageContext(
                "Engineer",
                "Example",
                stage = "Technical",
                interviewer = "  Sam Smith  ",
                date = date,
            )
        val message =
            MessageTemplates.render(MessageTemplate.THANK_YOU, context, ZoneId.of("Europe/London"))
        assertTrue(message.startsWith("Hi Sam,"))
        assertTrue(message.contains("Tuesday 2 June"))
        assertTrue(
            MessageTemplates.available("SCHEDULED", "Technical", date, date.plusSeconds(3600))
                .contains(MessageTemplate.THANK_YOU)
        )
    }

    @Test
    fun rejectedOffersAndClosedApplicationsGetOnlyAppropriateTemplates() {
        assertEquals(
            listOf(MessageTemplate.ASK_FEEDBACK),
            MessageTemplates.available("REJECTED", "Technical", null),
        )
        assertEquals(
            listOf(MessageTemplate.ACCEPT_OFFER, MessageTemplate.DECLINE_OFFER),
            MessageTemplates.available("OFFER_RECEIVED", "Offer", null),
        )
        for (outcome in
            listOf("NO_RESPONSE", "WITHDREW", "OFFER_ACCEPTED", "OFFER_DECLINED")) assertTrue(
            MessageTemplates.available(outcome, "Applied", null).isEmpty()
        )
    }
}
