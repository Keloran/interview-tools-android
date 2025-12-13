package tools.interviews.android.util

import java.net.URI

object LinkUtils {

    private val MEETING_PLATFORMS = listOf(
        Regex("""zoom\.us|zoom\.com""", RegexOption.IGNORE_CASE) to "Zoom",
        Regex("""zoomgov\.com""", RegexOption.IGNORE_CASE) to "ZoomGov",
        Regex("""teams\.microsoft\.com|microsoft\.teams|live\.com/meet""", RegexOption.IGNORE_CASE) to "Teams",
        Regex("""meet\.google\.com|hangouts\.google\.com|google\.com/hangouts|workspace\.google\.com/products/meet""", RegexOption.IGNORE_CASE) to "Google Meet",
        Regex("""webex\.com|webex""", RegexOption.IGNORE_CASE) to "Webex",
        Regex("""skype\.com""", RegexOption.IGNORE_CASE) to "Skype",
        Regex("""bluejeans\.com""", RegexOption.IGNORE_CASE) to "BlueJeans",
        Regex("""whereby\.com""", RegexOption.IGNORE_CASE) to "Whereby",
        Regex("""jitsi\.org|meet\.jit\.si""", RegexOption.IGNORE_CASE) to "Jitsi",
        Regex("""gotomeet|gotowebinar|goto\.com""", RegexOption.IGNORE_CASE) to "GoToMeeting",
        Regex("""chime\.aws|amazonchime\.com""", RegexOption.IGNORE_CASE) to "Amazon Chime",
        Regex("""slack\.com""", RegexOption.IGNORE_CASE) to "Slack",
        Regex("""discord\.(gg|com)""", RegexOption.IGNORE_CASE) to "Discord",
        Regex("""facetime|apple\.com/facetime""", RegexOption.IGNORE_CASE) to "FaceTime",
        Regex("""whatsapp\.com""", RegexOption.IGNORE_CASE) to "WhatsApp",
        Regex("""(^|\.)8x8\.vc""", RegexOption.IGNORE_CASE) to "8x8",
        Regex("""telegram\.(me|org)|(^|/)t\.me/""", RegexOption.IGNORE_CASE) to "Telegram",
        Regex("""signal\.org""", RegexOption.IGNORE_CASE) to "Signal"
    )

    /**
     * Infers the meeting platform name from a URL.
     * Returns the platform name (e.g., "Zoom", "Teams") or "Link" if unknown.
     */
    fun inferMeetingPlatform(link: String?): String? {
        if (link.isNullOrBlank()) return null

        // Try to extract hostname
        val host = try {
            val url = if (link.startsWith("http://") || link.startsWith("https://")) {
                URI(link)
            } else {
                URI("https://$link")
            }
            url.host ?: ""
        } catch (e: Exception) {
            ""
        }

        // Remove www. prefix
        val normalizedHost = host.replaceFirst(Regex("^www\\."), "")

        // Check each candidate
        for ((pattern, name) in MEETING_PLATFORMS) {
            if (pattern.containsMatchIn(link) || pattern.containsMatchIn(normalizedHost)) {
                return name
            }
        }

        return if (link.isNotBlank()) "Link" else null
    }

    /**
     * Checks if the link appears to be a meeting/video call link.
     */
    fun isMeetingLink(link: String?): Boolean {
        if (link.isNullOrBlank()) return false
        val platform = inferMeetingPlatform(link)
        return platform != null && platform != "Link"
    }
}
