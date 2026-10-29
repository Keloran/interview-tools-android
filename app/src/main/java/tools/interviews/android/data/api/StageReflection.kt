package tools.interviews.android.data.api

import com.google.gson.*
import java.time.LocalDate

object StageReflection {
    val fields = listOf("debriefNotes", "peopleMet", "questionsAsked", "nextStep")

    fun debriefFromApi(row: APIInterview): String =
        JsonObject()
            .apply {
                addProperty("rating", row.rating)
                addProperty("replyBy", row.replyBy)
                addProperty("debriefNotes", row.debriefNotes)
                addProperty("peopleMet", row.peopleMet)
                addProperty("questionsAsked", row.questionsAsked)
                addProperty("nextStep", row.nextStep)
            }
            .toString()

    fun feedbackFromApi(row: APIInterview): String =
        JsonObject()
            .apply {
                addProperty("type", row.feedbackType)
                addProperty("text", row.feedbackText)
            }
            .toString()

    fun debrief(values: Map<String, String>, rating: Int?, replyBy: String?): String =
        JsonObject()
            .apply {
                require(rating == null || rating in 1..5) {
                    "Choose a rating from 1 to 5, or leave it unknown."
                }
                require(
                    replyBy.isNullOrBlank() || runCatching { LocalDate.parse(replyBy) }.isSuccess
                ) {
                    "Use a YYYY-MM-DD reply-by date."
                }
                for (field in fields) addProperty(
                    field,
                    values[field]?.trim()?.takeIf { it.isNotBlank() },
                )
                addProperty("rating", rating)
                addProperty("replyBy", replyBy?.takeIf { it.isNotBlank() }?.plus("T00:00:00Z"))
            }
            .toString()

    fun feedback(type: String?, text: String): String =
        JsonObject()
            .apply {
                require(type == null || type in setOf("NONE", "GENERIC", "SPECIFIC")) {
                    "Choose a feedback type."
                }
                addProperty("type", type)
                addProperty("text", text.trim().takeIf { it.isNotBlank() })
            }
            .toString()

    fun prep(notes: String, ids: Collection<Int>): String =
        JsonObject()
            .apply {
                require(notes.length <= 12000 && ids.size <= 30 && ids.all { it > 0 }) {
                    "Use at most 12,000 characters and 30 stories."
                }
                addProperty("notes", notes)
                add(
                    "storyIds",
                    JsonArray().also { array -> ids.distinct().forEach { array.add(it) } },
                )
            }
            .toString()
}
