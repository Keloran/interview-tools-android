package tools.interviews.android.data.api

import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tools.interviews.android.data.CompanyDao
import tools.interviews.android.data.InterviewDao
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage

class SyncServiceMappingTest {

    private lateinit var syncService: SyncService
    private lateinit var mockInterviewDao: InterviewDao
    private lateinit var mockCompanyDao: CompanyDao

    @Before
    fun setup() {
        mockInterviewDao = mockk(relaxed = true)
        mockCompanyDao = mockk(relaxed = true)
        syncService = SyncService(mockInterviewDao, mockCompanyDao)
    }

    // ==================== Stage Mapping Tests ====================

    @Test
    fun `mapApiStageToLocal returns APPLIED for null`() {
        val result = syncService.mapApiStageToLocal(null)
        assertEquals(InterviewStage.APPLIED, result)
    }

    @Test
    fun `mapApiStageToLocal maps all valid stages`() {
        val testCases = mapOf(
            "Applied" to InterviewStage.APPLIED,
            "Phone Screen" to InterviewStage.PHONE_SCREEN,
            "First Stage" to InterviewStage.FIRST_STAGE,
            "Second Stage" to InterviewStage.SECOND_STAGE,
            "Third Stage" to InterviewStage.THIRD_STAGE,
            "Fourth Stage" to InterviewStage.FOURTH_STAGE,
            "Technical Test" to InterviewStage.TECHNICAL_TEST,
            "Technical Interview" to InterviewStage.TECHNICAL_INTERVIEW,
            "Final Stage" to InterviewStage.FINAL_STAGE,
            "Onsite" to InterviewStage.ONSITE,
            "Offer" to InterviewStage.OFFER
        )

        testCases.forEach { (input, expected) ->
            val result = syncService.mapApiStageToLocal(input)
            assertEquals("Failed for input: $input", expected, result)
        }
    }

    @Test
    fun `mapApiStageToLocal is case insensitive`() {
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("APPLIED"))
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("applied"))
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("ApPlIeD"))
        assertEquals(InterviewStage.PHONE_SCREEN, syncService.mapApiStageToLocal("PHONE SCREEN"))
        assertEquals(InterviewStage.PHONE_SCREEN, syncService.mapApiStageToLocal("phone screen"))
    }

    @Test
    fun `mapApiStageToLocal returns APPLIED for unknown stage`() {
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("Unknown Stage"))
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("Fifth Stage"))
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("HR Interview"))
    }

    @Test
    fun `mapApiStageToLocal returns APPLIED for empty string`() {
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal(""))
    }

    @Test
    fun `mapApiStageToLocal returns APPLIED for whitespace only`() {
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal("   "))
    }

    @Test
    fun `mapApiStageToLocal handles stage with extra whitespace`() {
        // Note: Current implementation doesn't trim, so this tests actual behavior
        assertEquals(InterviewStage.APPLIED, syncService.mapApiStageToLocal(" Applied "))
    }

    // ==================== Method Mapping Tests ====================

    @Test
    fun `mapApiMethodToLocal returns null for null`() {
        val result = syncService.mapApiMethodToLocal(null)
        assertNull(result)
    }

    @Test
    fun `mapApiMethodToLocal maps video call variants`() {
        assertEquals(InterviewMethod.VIDEO_CALL, syncService.mapApiMethodToLocal("video call"))
        assertEquals(InterviewMethod.VIDEO_CALL, syncService.mapApiMethodToLocal("video"))
        assertEquals(InterviewMethod.VIDEO_CALL, syncService.mapApiMethodToLocal("Video Call"))
        assertEquals(InterviewMethod.VIDEO_CALL, syncService.mapApiMethodToLocal("VIDEO"))
    }

    @Test
    fun `mapApiMethodToLocal maps phone call variants`() {
        assertEquals(InterviewMethod.PHONE_CALL, syncService.mapApiMethodToLocal("phone call"))
        assertEquals(InterviewMethod.PHONE_CALL, syncService.mapApiMethodToLocal("phone"))
        assertEquals(InterviewMethod.PHONE_CALL, syncService.mapApiMethodToLocal("Phone Call"))
        assertEquals(InterviewMethod.PHONE_CALL, syncService.mapApiMethodToLocal("PHONE"))
    }

    @Test
    fun `mapApiMethodToLocal maps in person variants`() {
        assertEquals(InterviewMethod.IN_PERSON, syncService.mapApiMethodToLocal("in person"))
        assertEquals(InterviewMethod.IN_PERSON, syncService.mapApiMethodToLocal("in_person"))
        assertEquals(InterviewMethod.IN_PERSON, syncService.mapApiMethodToLocal("onsite"))
        assertEquals(InterviewMethod.IN_PERSON, syncService.mapApiMethodToLocal("In Person"))
        assertEquals(InterviewMethod.IN_PERSON, syncService.mapApiMethodToLocal("ONSITE"))
    }

    @Test
    fun `mapApiMethodToLocal returns null for unknown method`() {
        assertNull(syncService.mapApiMethodToLocal("email"))
        assertNull(syncService.mapApiMethodToLocal("chat"))
        assertNull(syncService.mapApiMethodToLocal("virtual"))
        assertNull(syncService.mapApiMethodToLocal("remote"))
    }

    @Test
    fun `mapApiMethodToLocal returns null for empty string`() {
        assertNull(syncService.mapApiMethodToLocal(""))
    }

    @Test
    fun `mapApiMethodToLocal returns null for whitespace only`() {
        assertNull(syncService.mapApiMethodToLocal("   "))
    }

    // ==================== Outcome Mapping Tests ====================

    @Test
    fun `mapApiOutcomeToLocal returns SCHEDULED for null`() {
        val result = syncService.mapApiOutcomeToLocal(null)
        assertEquals(InterviewOutcome.SCHEDULED, result)
    }

    @Test
    fun `mapApiOutcomeToLocal maps all valid outcomes`() {
        val testCases = mapOf(
            "scheduled" to InterviewOutcome.SCHEDULED,
            "awaiting response" to InterviewOutcome.AWAITING_RESPONSE,
            "awaiting_response" to InterviewOutcome.AWAITING_RESPONSE,
            "pending" to InterviewOutcome.AWAITING_RESPONSE,
            "passed" to InterviewOutcome.PASSED,
            "rejected" to InterviewOutcome.REJECTED,
            "offer received" to InterviewOutcome.OFFER_RECEIVED,
            "offer_received" to InterviewOutcome.OFFER_RECEIVED,
            "offer accepted" to InterviewOutcome.OFFER_ACCEPTED,
            "offer_accepted" to InterviewOutcome.OFFER_ACCEPTED,
            "offer declined" to InterviewOutcome.OFFER_DECLINED,
            "offer_declined" to InterviewOutcome.OFFER_DECLINED,
            "withdrew" to InterviewOutcome.WITHDREW,
            "withdrawn" to InterviewOutcome.WITHDREW
        )

        testCases.forEach { (input, expected) ->
            val result = syncService.mapApiOutcomeToLocal(input)
            assertEquals("Failed for input: $input", expected, result)
        }
    }

    @Test
    fun `mapApiOutcomeToLocal is case insensitive`() {
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal("SCHEDULED"))
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal("Scheduled"))
        assertEquals(InterviewOutcome.PASSED, syncService.mapApiOutcomeToLocal("PASSED"))
        assertEquals(InterviewOutcome.PASSED, syncService.mapApiOutcomeToLocal("Passed"))
        assertEquals(InterviewOutcome.REJECTED, syncService.mapApiOutcomeToLocal("REJECTED"))
    }

    @Test
    fun `mapApiOutcomeToLocal returns SCHEDULED for unknown outcome`() {
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal("unknown"))
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal("in progress"))
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal("completed"))
    }

    @Test
    fun `mapApiOutcomeToLocal returns SCHEDULED for empty string`() {
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal(""))
    }

    @Test
    fun `mapApiOutcomeToLocal returns SCHEDULED for whitespace only`() {
        assertEquals(InterviewOutcome.SCHEDULED, syncService.mapApiOutcomeToLocal("   "))
    }

    @Test
    fun `mapApiOutcomeToLocal handles pending as awaiting response`() {
        // "pending" is an alias for AWAITING_RESPONSE
        assertEquals(InterviewOutcome.AWAITING_RESPONSE, syncService.mapApiOutcomeToLocal("pending"))
        assertEquals(InterviewOutcome.AWAITING_RESPONSE, syncService.mapApiOutcomeToLocal("PENDING"))
    }

    @Test
    fun `mapApiOutcomeToLocal handles withdrawn vs withdrew`() {
        // Both should map to WITHDREW
        assertEquals(InterviewOutcome.WITHDREW, syncService.mapApiOutcomeToLocal("withdrew"))
        assertEquals(InterviewOutcome.WITHDREW, syncService.mapApiOutcomeToLocal("withdrawn"))
        assertEquals(InterviewOutcome.WITHDREW, syncService.mapApiOutcomeToLocal("WITHDRAWN"))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `mapApiOutcomeToLocal handles mixed case with underscores`() {
        assertEquals(InterviewOutcome.AWAITING_RESPONSE, syncService.mapApiOutcomeToLocal("Awaiting_Response"))
        assertEquals(InterviewOutcome.OFFER_RECEIVED, syncService.mapApiOutcomeToLocal("Offer_Received"))
    }

    @Test
    fun `mapApiStageToLocal handles display names correctly`() {
        // Test that the display names from InterviewStage enum are properly mapped
        InterviewStage.entries.forEach { stage ->
            val result = syncService.mapApiStageToLocal(stage.displayName)
            assertEquals("Failed to map display name: ${stage.displayName}", stage, result)
        }
    }

    @Test
    fun `mapApiMethodToLocal handles all InterviewMethod display names`() {
        // VIDEO_CALL -> "Video Call", PHONE_CALL -> "Phone Call", IN_PERSON -> "In Person"
        assertEquals(InterviewMethod.VIDEO_CALL, syncService.mapApiMethodToLocal("Video Call"))
        assertEquals(InterviewMethod.PHONE_CALL, syncService.mapApiMethodToLocal("Phone Call"))
        assertEquals(InterviewMethod.IN_PERSON, syncService.mapApiMethodToLocal("In Person"))
    }
}