package tools.interviews.android.data.api

import com.google.gson.JsonParser
import java.time.*
import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.model.*
import tools.interviews.android.reminders.DebriefPolicy

class StageReflectionTest {
    @Test
    fun debriefFieldsDoNotOverwriteSeparatePreparationAndUnknownValuesStayUnknown() {
        val value =
            JsonParser.parseString(
                    StageReflection.debrief(
                        mapOf("questionsAsked" to "Explain indexes"),
                        null,
                        null,
                    )
                )
                .asJsonObject
        assertEquals("Explain indexes", value.get("questionsAsked").asString)
        assertTrue(value.get("rating").isJsonNull)
        assertTrue(value.get("replyBy").isJsonNull)
        assertFalse(value.has("prepNotes"))
        assertTrue(runCatching { StageReflection.debrief(emptyMap(), 6, null) }.isFailure)
        assertTrue(runCatching { StageReflection.debrief(emptyMap(), 3, "2026-02-30") }.isFailure)
    }

    @Test
    fun feedbackDistinguishesUnknownFromExplicitlyNoFeedback() {
        val unknown = JsonParser.parseString(StageReflection.feedback(null, "")).asJsonObject
        assertTrue(unknown.get("type").isJsonNull)
        val none = JsonParser.parseString(StageReflection.feedback("NONE", "")).asJsonObject
        assertEquals("NONE", none.get("type").asString)
        assertTrue(runCatching { StageReflection.feedback("FUTURE", "") }.isFailure)
    }

    @Test
    fun preparationHasExplicitNotesAndStorySelectionsIncludingClears() {
        val value = JsonParser.parseString(StageReflection.prep("", listOf(1, 1, 2))).asJsonObject
        assertEquals("", value.get("notes").asString)
        assertEquals(listOf(1, 2), value.getAsJsonArray("storyIds").map { it.asInt })
        assertTrue(runCatching { StageReflection.prep("x".repeat(12001), emptyList()) }.isFailure)
        assertTrue(runCatching { StageReflection.prep("", listOf(-1)) }.isFailure)
    }

    @Test
    fun starFieldsUseServerLimitsAndNormalizedTags() {
        val body =
            StoryFields.body(
                mapOf("title" to " Example ", "action" to " Led the team "),
                "Leadership, teamwork,LEADERSHIP",
            )
        assertEquals("Example", body.get("title").asString)
        assertEquals(
            listOf("leadership", "teamwork"),
            body.getAsJsonArray("tags").map { it.asString },
        )
        assertTrue(runCatching { StoryFields.body(emptyMap(), "") }.isFailure)
        assertTrue(
            runCatching {
                    StoryFields.body(mapOf("title" to "Example", "result" to "x".repeat(8001)), "")
                }
                .isFailure
        )
        assertTrue(
            runCatching {
                    StoryFields.body(
                        mapOf("title" to "Example"),
                        (1..13).joinToString(",") { "tag$it" },
                    )
                }
                .isFailure
        )
    }

    private fun meeting() =
        Interview(
            jobTitle = "Engineer",
            companyName = "Example",
            stage = InterviewStage.FIRST_STAGE,
            applicationDate = LocalDate.of(2026, 1, 1),
            interviewInstant = Instant.parse("2026-03-29T00:30:00Z"),
            durationMinutes = 90,
        )

    @Test
    fun remindersUseElapsedMeetingEndAndNeverPromptOffersOrAmbiguousLegacyTimes() {
        val row = meeting()
        assertEquals(Instant.parse("2026-03-29T02:00:00Z"), DebriefPolicy.end(row))
        assertFalse(DebriefPolicy.due(row, Instant.parse("2026-03-29T01:59:00Z")))
        assertTrue(DebriefPolicy.due(row, Instant.parse("2026-03-29T02:00:00Z")))
        assertFalse(DebriefPolicy.due(row, Instant.parse("2026-04-02T02:00:00Z")))
        assertFalse(DebriefPolicy.scheduled(row.copy(stage = InterviewStage.OFFER)))
        assertFalse(
            DebriefPolicy.scheduled(
                row.copy(
                    interviewInstant = null,
                    interviewDate = LocalDateTime.parse("2026-03-29T00:30"),
                )
            )
        )
    }

    @Test
    fun remindersRespectCompletedDebriefsClosedStagesAndAlreadyPromptedMeeting() {
        val row = meeting()
        assertFalse(DebriefPolicy.scheduled(row.copy(debriefedAt = "2026-03-29T03:00:00Z")))
        assertFalse(DebriefPolicy.scheduled(row.copy(outcome = InterviewOutcome.NO_RESPONSE)))
        assertFalse(
            DebriefPolicy.scheduled(
                row.copy(debriefPromptedFor = DebriefPolicy.end(row).toString())
            )
        )
        assertTrue(
            DebriefPolicy.scheduled(row.copy(debriefPromptedFor = "an earlier meeting time"))
        )
    }
}
