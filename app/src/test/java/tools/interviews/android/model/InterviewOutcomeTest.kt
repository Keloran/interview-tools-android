package tools.interviews.android.model

import org.junit.Assert.*
import org.junit.Test

class InterviewOutcomeTest {

    @Test
    fun `InterviewOutcome has correct number of values`() {
        assertEquals(8, InterviewOutcome.entries.size)
    }

    @Test
    fun `InterviewOutcome SCHEDULED has correct displayName`() {
        assertEquals("Scheduled", InterviewOutcome.SCHEDULED.displayName)
    }

    @Test
    fun `InterviewOutcome AWAITING_RESPONSE has correct displayName`() {
        assertEquals("Awaiting Response", InterviewOutcome.AWAITING_RESPONSE.displayName)
    }

    @Test
    fun `InterviewOutcome PASSED has correct displayName`() {
        assertEquals("Passed", InterviewOutcome.PASSED.displayName)
    }

    @Test
    fun `InterviewOutcome REJECTED has correct displayName`() {
        assertEquals("Rejected", InterviewOutcome.REJECTED.displayName)
    }

    @Test
    fun `InterviewOutcome OFFER_RECEIVED has correct displayName`() {
        assertEquals("Offer Received", InterviewOutcome.OFFER_RECEIVED.displayName)
    }

    @Test
    fun `InterviewOutcome OFFER_ACCEPTED has correct displayName`() {
        assertEquals("Offer Accepted", InterviewOutcome.OFFER_ACCEPTED.displayName)
    }

    @Test
    fun `InterviewOutcome OFFER_DECLINED has correct displayName`() {
        assertEquals("Offer Declined", InterviewOutcome.OFFER_DECLINED.displayName)
    }

    @Test
    fun `InterviewOutcome WITHDREW has correct displayName`() {
        assertEquals("Withdrew", InterviewOutcome.WITHDREW.displayName)
    }

    @Test
    fun `all outcomes have non-empty displayNames`() {
        InterviewOutcome.entries.forEach { outcome ->
            assertTrue(
                "Outcome ${outcome.name} has empty displayName",
                outcome.displayName.isNotEmpty()
            )
        }
    }

    @Test
    fun `all outcomes have unique displayNames`() {
        val displayNames = InterviewOutcome.entries.map { it.displayName }
        assertEquals(
            "Duplicate displayNames found",
            displayNames.size,
            displayNames.toSet().size
        )
    }

    @Test
    fun `all outcomes have non-zero colorRes`() {
        InterviewOutcome.entries.forEach { outcome ->
            assertTrue(
                "Outcome ${outcome.name} has zero colorRes",
                outcome.colorRes != 0
            )
        }
    }

    @Test
    fun `valueOf returns correct outcome for valid names`() {
        InterviewOutcome.entries.forEach { outcome ->
            assertEquals(outcome, InterviewOutcome.valueOf(outcome.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewOutcome.valueOf("INVALID_OUTCOME")
        }
    }

    @Test
    fun `valueOf throws for lowercase name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewOutcome.valueOf("scheduled")
        }
    }

    @Test
    fun `valueOf throws for display name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewOutcome.valueOf("Scheduled")
        }
    }

    @Test
    fun `valueOf throws for empty string`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewOutcome.valueOf("")
        }
    }

    @Test
    fun `outcomes are in expected order`() {
        val expectedOrder = listOf(
            InterviewOutcome.SCHEDULED,
            InterviewOutcome.AWAITING_RESPONSE,
            InterviewOutcome.PASSED,
            InterviewOutcome.REJECTED,
            InterviewOutcome.OFFER_RECEIVED,
            InterviewOutcome.OFFER_ACCEPTED,
            InterviewOutcome.OFFER_DECLINED,
            InterviewOutcome.WITHDREW
        )
        assertEquals(expectedOrder, InterviewOutcome.entries.toList())
    }

    @Test
    fun `ordinal values are sequential starting from 0`() {
        InterviewOutcome.entries.forEachIndexed { index, outcome ->
            assertEquals("Ordinal mismatch for ${outcome.name}", index, outcome.ordinal)
        }
    }

    @Test
    fun `positive outcomes exist`() {
        // Test that we have positive outcomes
        val positiveOutcomes = listOf(
            InterviewOutcome.PASSED,
            InterviewOutcome.OFFER_RECEIVED,
            InterviewOutcome.OFFER_ACCEPTED
        )
        positiveOutcomes.forEach { outcome ->
            assertTrue(
                "Positive outcome ${outcome.name} should exist",
                InterviewOutcome.entries.contains(outcome)
            )
        }
    }

    @Test
    fun `negative outcomes exist`() {
        // Test that we have negative outcomes
        val negativeOutcomes = listOf(
            InterviewOutcome.REJECTED,
            InterviewOutcome.OFFER_DECLINED,
            InterviewOutcome.WITHDREW
        )
        negativeOutcomes.forEach { outcome ->
            assertTrue(
                "Negative outcome ${outcome.name} should exist",
                InterviewOutcome.entries.contains(outcome)
            )
        }
    }

    @Test
    fun `neutral outcomes exist`() {
        // Test that we have neutral/pending outcomes
        val neutralOutcomes = listOf(
            InterviewOutcome.SCHEDULED,
            InterviewOutcome.AWAITING_RESPONSE
        )
        neutralOutcomes.forEach { outcome ->
            assertTrue(
                "Neutral outcome ${outcome.name} should exist",
                InterviewOutcome.entries.contains(outcome)
            )
        }
    }

    @Test
    fun `offer related outcomes have consistent naming`() {
        val offerOutcomes = InterviewOutcome.entries.filter {
            it.name.startsWith("OFFER_")
        }
        assertEquals(3, offerOutcomes.size)
        assertTrue(offerOutcomes.any { it == InterviewOutcome.OFFER_RECEIVED })
        assertTrue(offerOutcomes.any { it == InterviewOutcome.OFFER_ACCEPTED })
        assertTrue(offerOutcomes.any { it == InterviewOutcome.OFFER_DECLINED })
    }
}
