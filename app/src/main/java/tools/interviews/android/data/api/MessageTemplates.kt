package tools.interviews.android.data.api

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MessageContext(
    val jobTitle: String,
    val company: String,
    val clientCompany: String? = null,
    val stage: String? = null,
    val interviewer: String? = null,
    val date: Instant? = null,
)

enum class MessageTemplate(val label: String) {
    THANK_YOU("Thank you"),
    FOLLOW_UP("Follow up"),
    WITHDRAW("Withdraw"),
    ACCEPT_OFFER("Accept offer"),
    DECLINE_OFFER("Decline offer"),
    ASK_FEEDBACK("Ask for feedback"),
}

object MessageTemplates {
    fun available(
        outcome: String?,
        stage: String?,
        date: Instant?,
        now: Instant = Instant.now(),
    ): List<MessageTemplate> {
        val state = outcome.orEmpty().uppercase()
        if (state in setOf("OFFER_ACCEPTED", "OFFER_DECLINED", "WITHDREW", "NO_RESPONSE"))
            return emptyList()
        if (state == "REJECTED") return listOf(MessageTemplate.ASK_FEEDBACK)
        if (state == "OFFER_RECEIVED" || stage.equals("Offer", true))
            return listOf(MessageTemplate.ACCEPT_OFFER, MessageTemplate.DECLINE_OFFER)
        val happened = !stage.equals("Applied", true) && date?.isBefore(now) == true
        return if (happened)
            listOf(MessageTemplate.THANK_YOU, MessageTemplate.FOLLOW_UP, MessageTemplate.WITHDRAW)
        else listOf(MessageTemplate.FOLLOW_UP, MessageTemplate.WITHDRAW)
    }

    fun render(
        template: MessageTemplate,
        ctx: MessageContext,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val role =
            if (!ctx.clientCompany.isNullOrBlank())
                "the ${ctx.jobTitle} role with ${ctx.clientCompany}"
            else "the ${ctx.jobTitle} role at ${ctx.company}"
        val name =
            ctx.interviewer?.trim()?.takeIf { it.isNotBlank() }?.split(Regex("\\s+"))?.first()
        val greeting = if (name == null) "Hi," else "Hi $name,"
        val interview =
            listOfNotNull(
                    "my",
                    ctx.stage?.takeIf { it.isNotBlank() && !it.equals("Applied", true) },
                    "interview",
                    ctx.date?.let {
                        "on ${it.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.UK))}"
                    },
                )
                .joinToString(" ")
        val body =
            when (template) {
                MessageTemplate.THANK_YOU ->
                    "Thank you for taking the time to speak with me in $interview about $role. I enjoyed our conversation and learning more about the team.\n\nI'm very interested in the role and look forward to hearing about next steps."
                MessageTemplate.FOLLOW_UP ->
                    (if (!ctx.stage.isNullOrBlank() && !ctx.stage.equals("Applied", true))
                        "I wanted to follow up after $interview for $role."
                    else "I wanted to follow up on my application for $role.") +
                        "\n\nI'm still very interested and would be grateful for an update on where things are."
                MessageTemplate.WITHDRAW ->
                    "Thank you for considering me for $role. I've decided to withdraw my application.\n\nI appreciate the time you've spent on this and hope our paths cross again."
                MessageTemplate.ACCEPT_OFFER ->
                    "Thank you for the offer for $role. I'm delighted to accept.\n\nPlease let me know what you need from me and the next steps."
                MessageTemplate.DECLINE_OFFER ->
                    "Thank you for the offer for $role. After careful thought I've decided to decline.\n\nI really appreciate the time everyone spent with me and hope we stay in touch."
                MessageTemplate.ASK_FEEDBACK ->
                    "Thank you for letting me know about $role. I'm disappointed, but I appreciate you getting back to me.\n\nIf you have a moment, I'd be grateful for any feedback on how I could improve."
            }
        return "$greeting\n\n$body\n\nThanks"
    }
}
