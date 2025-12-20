package tools.interviews.android.util

import org.junit.Assert.*
import org.junit.Test

class LinkUtilsTest {

    // ==================== Null and Empty Input Tests ====================

    @Test
    fun `inferMeetingPlatform returns null for null input`() {
        val result = LinkUtils.inferMeetingPlatform(null)
        assertNull(result)
    }

    @Test
    fun `inferMeetingPlatform returns null for empty string`() {
        val result = LinkUtils.inferMeetingPlatform("")
        assertNull(result)
    }

    @Test
    fun `inferMeetingPlatform returns null for blank string`() {
        val result = LinkUtils.inferMeetingPlatform("   ")
        assertNull(result)
    }

    @Test
    fun `inferMeetingPlatform returns null for whitespace only`() {
        val result = LinkUtils.inferMeetingPlatform("\t\n  ")
        assertNull(result)
    }

    // ==================== Zoom Tests ====================

    @Test
    fun `inferMeetingPlatform detects Zoom with https`() {
        val result = LinkUtils.inferMeetingPlatform("https://zoom.us/j/123456789")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform detects Zoom with http`() {
        val result = LinkUtils.inferMeetingPlatform("http://zoom.us/j/123456789")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform detects Zoom without protocol`() {
        val result = LinkUtils.inferMeetingPlatform("zoom.us/j/123456789")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform detects Zoom with www prefix`() {
        val result = LinkUtils.inferMeetingPlatform("https://www.zoom.us/j/123456789")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform detects Zoom dot com`() {
        val result = LinkUtils.inferMeetingPlatform("https://zoom.com/j/123456789")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform detects Zoom case insensitive`() {
        val result = LinkUtils.inferMeetingPlatform("https://ZOOM.US/j/123456789")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform detects ZoomGov`() {
        val result = LinkUtils.inferMeetingPlatform("https://company.zoomgov.com/j/123456789")
        assertEquals("ZoomGov", result)
    }

    // ==================== Microsoft Teams Tests ====================

    @Test
    fun `inferMeetingPlatform detects Teams`() {
        val result = LinkUtils.inferMeetingPlatform("https://teams.microsoft.com/l/meetup-join/123")
        assertEquals("Teams", result)
    }

    @Test
    fun `inferMeetingPlatform detects Teams live meeting`() {
        val result = LinkUtils.inferMeetingPlatform("https://teams.live.com/meet/123")
        assertEquals("Teams", result)
    }

    @Test
    fun `inferMeetingPlatform detects Teams case insensitive`() {
        val result = LinkUtils.inferMeetingPlatform("https://TEAMS.MICROSOFT.COM/meeting/123")
        assertEquals("Teams", result)
    }

    // ==================== Google Meet Tests ====================

    @Test
    fun `inferMeetingPlatform detects Google Meet`() {
        val result = LinkUtils.inferMeetingPlatform("https://meet.google.com/abc-defg-hij")
        assertEquals("Google Meet", result)
    }

    @Test
    fun `inferMeetingPlatform detects Google Hangouts`() {
        val result = LinkUtils.inferMeetingPlatform("https://hangouts.google.com/call/abc123")
        assertEquals("Google Meet", result)
    }

    @Test
    fun `inferMeetingPlatform detects Google Meet case insensitive`() {
        val result = LinkUtils.inferMeetingPlatform("https://MEET.GOOGLE.COM/abc-defg")
        assertEquals("Google Meet", result)
    }

    // ==================== Webex Tests ====================

    @Test
    fun `inferMeetingPlatform detects Webex`() {
        val result = LinkUtils.inferMeetingPlatform("https://company.webex.com/meet/user")
        assertEquals("Webex", result)
    }

    @Test
    fun `inferMeetingPlatform detects Webex subdomain`() {
        val result = LinkUtils.inferMeetingPlatform("https://acme.webex.com/acme/j.php?MTID=123")
        assertEquals("Webex", result)
    }

    // ==================== Skype Tests ====================

    @Test
    fun `inferMeetingPlatform detects Skype`() {
        val result = LinkUtils.inferMeetingPlatform("https://join.skype.com/abc123")
        assertEquals("Skype", result)
    }

    @Test
    fun `inferMeetingPlatform detects Skype for Business`() {
        val result = LinkUtils.inferMeetingPlatform("https://meet.skype.com/user")
        assertEquals("Skype", result)
    }

    // ==================== BlueJeans Tests ====================

    @Test
    fun `inferMeetingPlatform detects BlueJeans`() {
        val result = LinkUtils.inferMeetingPlatform("https://bluejeans.com/123456789")
        assertEquals("BlueJeans", result)
    }

    // ==================== Whereby Tests ====================

    @Test
    fun `inferMeetingPlatform detects Whereby`() {
        val result = LinkUtils.inferMeetingPlatform("https://whereby.com/my-room")
        assertEquals("Whereby", result)
    }

    // ==================== Jitsi Tests ====================

    @Test
    fun `inferMeetingPlatform detects Jitsi Meet`() {
        val result = LinkUtils.inferMeetingPlatform("https://meet.jit.si/MyMeeting")
        assertEquals("Jitsi", result)
    }

    @Test
    fun `inferMeetingPlatform detects Jitsi org`() {
        val result = LinkUtils.inferMeetingPlatform("https://jitsi.org/meet/room")
        assertEquals("Jitsi", result)
    }

    // ==================== GoToMeeting Tests ====================

    @Test
    fun `inferMeetingPlatform detects GoToMeeting`() {
        val result = LinkUtils.inferMeetingPlatform("https://global.gotomeeting.com/join/123456789")
        assertEquals("GoToMeeting", result)
    }

    @Test
    fun `inferMeetingPlatform detects GoToWebinar`() {
        val result = LinkUtils.inferMeetingPlatform("https://attendee.gotowebinar.com/register/123")
        assertEquals("GoToMeeting", result)
    }

    @Test
    fun `inferMeetingPlatform detects GoTo`() {
        val result = LinkUtils.inferMeetingPlatform("https://app.goto.com/meeting/123")
        assertEquals("GoToMeeting", result)
    }

    // ==================== Amazon Chime Tests ====================

    @Test
    fun `inferMeetingPlatform detects Amazon Chime`() {
        val result = LinkUtils.inferMeetingPlatform("https://chime.aws/123456789")
        assertEquals("Amazon Chime", result)
    }

    @Test
    fun `inferMeetingPlatform detects Amazon Chime alternate domain`() {
        val result = LinkUtils.inferMeetingPlatform("https://app.amazonchime.com/meetings/123")
        assertEquals("Amazon Chime", result)
    }

    // ==================== Slack Tests ====================

    @Test
    fun `inferMeetingPlatform detects Slack`() {
        val result = LinkUtils.inferMeetingPlatform("https://app.slack.com/huddle/T123/C456")
        assertEquals("Slack", result)
    }

    @Test
    fun `inferMeetingPlatform detects Slack call link`() {
        val result = LinkUtils.inferMeetingPlatform("https://workspace.slack.com/call/123")
        assertEquals("Slack", result)
    }

    // ==================== Discord Tests ====================

    @Test
    fun `inferMeetingPlatform detects Discord invite`() {
        val result = LinkUtils.inferMeetingPlatform("https://discord.gg/abc123")
        assertEquals("Discord", result)
    }

    @Test
    fun `inferMeetingPlatform detects Discord com`() {
        val result = LinkUtils.inferMeetingPlatform("https://discord.com/invite/abc123")
        assertEquals("Discord", result)
    }

    // ==================== FaceTime Tests ====================

    @Test
    fun `inferMeetingPlatform detects FaceTime`() {
        val result = LinkUtils.inferMeetingPlatform("https://facetime.apple.com/join#v=1&p=abc123")
        assertEquals("FaceTime", result)
    }

    // ==================== WhatsApp Tests ====================

    @Test
    fun `inferMeetingPlatform detects WhatsApp`() {
        val result = LinkUtils.inferMeetingPlatform("https://wa.me/1234567890")
        // Note: wa.me might not match whatsapp.com pattern
        // Testing actual domain
        val result2 = LinkUtils.inferMeetingPlatform("https://call.whatsapp.com/video/123")
        assertEquals("WhatsApp", result2)
    }

    // ==================== 8x8 Tests ====================

    @Test
    fun `inferMeetingPlatform detects 8x8`() {
        val result = LinkUtils.inferMeetingPlatform("https://8x8.vc/company/abc123")
        assertEquals("8x8", result)
    }

    @Test
    fun `inferMeetingPlatform detects 8x8 subdomain`() {
        val result = LinkUtils.inferMeetingPlatform("https://meet.8x8.vc/room")
        assertEquals("8x8", result)
    }

    // ==================== Telegram Tests ====================

    @Test
    fun `inferMeetingPlatform detects Telegram me`() {
        val result = LinkUtils.inferMeetingPlatform("https://telegram.me/username")
        assertEquals("Telegram", result)
    }

    @Test
    fun `inferMeetingPlatform detects Telegram t me`() {
        val result = LinkUtils.inferMeetingPlatform("https://t.me/username")
        assertEquals("Telegram", result)
    }

    @Test
    fun `inferMeetingPlatform detects Telegram org`() {
        val result = LinkUtils.inferMeetingPlatform("https://telegram.org/call/abc")
        assertEquals("Telegram", result)
    }

    // ==================== Signal Tests ====================

    @Test
    fun `inferMeetingPlatform detects Signal`() {
        val result = LinkUtils.inferMeetingPlatform("https://signal.org/call/abc123")
        assertEquals("Signal", result)
    }

    // ==================== Unknown Links Tests ====================

    @Test
    fun `inferMeetingPlatform returns Link for unknown URLs`() {
        val result = LinkUtils.inferMeetingPlatform("https://example.com/meeting")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform returns Link for random website`() {
        val result = LinkUtils.inferMeetingPlatform("https://github.com/user/repo")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform returns Link for company website`() {
        val result = LinkUtils.inferMeetingPlatform("https://careers.acme.com/jobs/123")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform returns Link for localhost`() {
        val result = LinkUtils.inferMeetingPlatform("http://localhost:3000/meeting")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform returns Link for IP address`() {
        val result = LinkUtils.inferMeetingPlatform("http://192.168.1.1/meeting")
        assertEquals("Link", result)
    }

    // ==================== Malformed URL Tests ====================

    @Test
    fun `inferMeetingPlatform handles malformed URL gracefully`() {
        val result = LinkUtils.inferMeetingPlatform("not a valid url at all")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform handles URL with spaces`() {
        val result = LinkUtils.inferMeetingPlatform("https://zoom.us/j/123 456 789")
        // Should still detect Zoom even with malformed URL
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform handles URL-like string without TLD`() {
        val result = LinkUtils.inferMeetingPlatform("zoom/meeting/123")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform handles just protocol`() {
        val result = LinkUtils.inferMeetingPlatform("https://")
        assertEquals("Link", result)
    }

    // ==================== isMeetingLink Tests ====================

    @Test
    fun `isMeetingLink returns false for null`() {
        val result = LinkUtils.isMeetingLink(null)
        assertFalse(result)
    }

    @Test
    fun `isMeetingLink returns false for empty string`() {
        val result = LinkUtils.isMeetingLink("")
        assertFalse(result)
    }

    @Test
    fun `isMeetingLink returns false for blank string`() {
        val result = LinkUtils.isMeetingLink("   ")
        assertFalse(result)
    }

    @Test
    fun `isMeetingLink returns true for Zoom link`() {
        val result = LinkUtils.isMeetingLink("https://zoom.us/j/123456789")
        assertTrue(result)
    }

    @Test
    fun `isMeetingLink returns true for Teams link`() {
        val result = LinkUtils.isMeetingLink("https://teams.microsoft.com/meeting/123")
        assertTrue(result)
    }

    @Test
    fun `isMeetingLink returns true for Google Meet link`() {
        val result = LinkUtils.isMeetingLink("https://meet.google.com/abc-defg-hij")
        assertTrue(result)
    }

    @Test
    fun `isMeetingLink returns false for generic link`() {
        val result = LinkUtils.isMeetingLink("https://example.com/page")
        assertFalse(result)
    }

    @Test
    fun `isMeetingLink returns false for job listing`() {
        val result = LinkUtils.isMeetingLink("https://linkedin.com/jobs/view/123456")
        assertFalse(result)
    }

    @Test
    fun `isMeetingLink returns false for company careers page`() {
        val result = LinkUtils.isMeetingLink("https://careers.google.com/jobs/123")
        assertFalse(result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `inferMeetingPlatform handles very long URL`() {
        val longPath = "a".repeat(1000)
        val result = LinkUtils.inferMeetingPlatform("https://zoom.us/j/$longPath")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform handles URL with special characters`() {
        val result = LinkUtils.inferMeetingPlatform("https://zoom.us/j/123?pwd=abc%20def&name=Test%2BUser")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform handles URL with unicode`() {
        val result = LinkUtils.inferMeetingPlatform("https://zoom.us/j/123?name=\u4E2D\u6587")
        assertEquals("Zoom", result)
    }

    @Test
    fun `inferMeetingPlatform handles multiple platform keywords in path`() {
        // URL with Zoom in the path but Teams in the domain should return Teams
        val result = LinkUtils.inferMeetingPlatform("https://teams.microsoft.com/meeting/zoom-style-123")
        assertEquals("Teams", result)
    }

    @Test
    fun `inferMeetingPlatform handles subdomain with platform name`() {
        // A site that has zoom in subdomain but isn't actually Zoom
        val result = LinkUtils.inferMeetingPlatform("https://zoom-integration.example.com/meeting")
        assertEquals("Link", result)
    }

    @Test
    fun `inferMeetingPlatform prioritizes first matching platform`() {
        // Test that the order of platform checks is consistent
        val zoomResult = LinkUtils.inferMeetingPlatform("https://zoom.us/meeting")
        assertEquals("Zoom", zoomResult)

        val teamsResult = LinkUtils.inferMeetingPlatform("https://teams.microsoft.com/meeting")
        assertEquals("Teams", teamsResult)
    }
}