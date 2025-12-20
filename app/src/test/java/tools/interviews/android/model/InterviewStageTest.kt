package tools.interviews.android.model

import org.junit.Assert.*
import org.junit.Test

class InterviewStageTest {

    @Test
    fun `InterviewStage has correct number of values`() {
        assertEquals(11, InterviewStage.entries.size)
    }

    @Test
    fun `InterviewStage APPLIED has correct displayName`() {
        assertEquals("Applied", InterviewStage.APPLIED.displayName)
    }

    @Test
    fun `InterviewStage PHONE_SCREEN has correct displayName`() {
        assertEquals("Phone Screen", InterviewStage.PHONE_SCREEN.displayName)
    }

    @Test
    fun `InterviewStage FIRST_STAGE has correct displayName`() {
        assertEquals("First Stage", InterviewStage.FIRST_STAGE.displayName)
    }

    @Test
    fun `InterviewStage SECOND_STAGE has correct displayName`() {
        assertEquals("Second Stage", InterviewStage.SECOND_STAGE.displayName)
    }

    @Test
    fun `InterviewStage THIRD_STAGE has correct displayName`() {
        assertEquals("Third Stage", InterviewStage.THIRD_STAGE.displayName)
    }

    @Test
    fun `InterviewStage FOURTH_STAGE has correct displayName`() {
        assertEquals("Fourth Stage", InterviewStage.FOURTH_STAGE.displayName)
    }

    @Test
    fun `InterviewStage TECHNICAL_TEST has correct displayName`() {
        assertEquals("Technical Test", InterviewStage.TECHNICAL_TEST.displayName)
    }

    @Test
    fun `InterviewStage TECHNICAL_INTERVIEW has correct displayName`() {
        assertEquals("Technical Interview", InterviewStage.TECHNICAL_INTERVIEW.displayName)
    }

    @Test
    fun `InterviewStage FINAL_STAGE has correct displayName`() {
        assertEquals("Final Stage", InterviewStage.FINAL_STAGE.displayName)
    }

    @Test
    fun `InterviewStage ONSITE has correct displayName`() {
        assertEquals("Onsite", InterviewStage.ONSITE.displayName)
    }

    @Test
    fun `InterviewStage OFFER has correct displayName`() {
        assertEquals("Offer", InterviewStage.OFFER.displayName)
    }

    @Test
    fun `all stages have non-empty displayNames`() {
        InterviewStage.entries.forEach { stage ->
            assertTrue(
                "Stage ${stage.name} has empty displayName",
                stage.displayName.isNotEmpty()
            )
        }
    }

    @Test
    fun `all stages have unique displayNames`() {
        val displayNames = InterviewStage.entries.map { it.displayName }
        assertEquals(
            "Duplicate displayNames found",
            displayNames.size,
            displayNames.toSet().size
        )
    }

    @Test
    fun `valueOf returns correct stage for valid names`() {
        InterviewStage.entries.forEach { stage ->
            assertEquals(stage, InterviewStage.valueOf(stage.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewStage.valueOf("INVALID_STAGE")
        }
    }

    @Test
    fun `valueOf throws for lowercase name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewStage.valueOf("applied")
        }
    }

    @Test
    fun `valueOf throws for display name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewStage.valueOf("Applied")
        }
    }

    @Test
    fun `stages are in expected order`() {
        val expectedOrder = listOf(
            InterviewStage.APPLIED,
            InterviewStage.PHONE_SCREEN,
            InterviewStage.FIRST_STAGE,
            InterviewStage.SECOND_STAGE,
            InterviewStage.THIRD_STAGE,
            InterviewStage.FOURTH_STAGE,
            InterviewStage.TECHNICAL_TEST,
            InterviewStage.TECHNICAL_INTERVIEW,
            InterviewStage.FINAL_STAGE,
            InterviewStage.ONSITE,
            InterviewStage.OFFER
        )
        assertEquals(expectedOrder, InterviewStage.entries.toList())
    }

    @Test
    fun `ordinal values are sequential starting from 0`() {
        InterviewStage.entries.forEachIndexed { index, stage ->
            assertEquals("Ordinal mismatch for ${stage.name}", index, stage.ordinal)
        }
    }
}