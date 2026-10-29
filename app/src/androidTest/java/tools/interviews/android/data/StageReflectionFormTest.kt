package tools.interviews.android.data

import android.content.Intent
import android.widget.AutoCompleteTextView
import androidx.core.widget.doAfterTextChanged
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonParser
import java.time.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import tools.interviews.android.*
import tools.interviews.android.data.api.StageReflection
import tools.interviews.android.model.*

@RunWith(AndroidJUnit4::class)
class StageReflectionFormTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context
        get() = instrumentation.targetContext

    private val app
        get() = context.applicationContext as InterviewApplication

    private var id: Long = -1

    @After fun cleanup() = runBlocking { if (id > 0) app.database.interviewDao().deleteById(id) }

    private fun awaitText(
        scenario: ActivityScenario<StageReflectionActivity>,
        viewId: Int,
        text: String,
    ) {
        val loaded = CountDownLatch(1)
        scenario.onActivity { activity ->
            val field = activity.findViewById<TextInputEditText>(viewId)
            if (field.text.toString() == text) loaded.countDown()
            else field.doAfterTextChanged { if (it.toString() == text) loaded.countDown() }
        }
        assertTrue("Form did not load $text", loaded.await(10, TimeUnit.SECONDS))
    }

    private fun enabled(scenario: ActivityScenario<StageReflectionActivity>, viewId: Int) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var enabled = false
        while (!enabled && System.nanoTime() < until) {
            scenario.onActivity { enabled = it.findViewById<android.view.View>(viewId).isEnabled }
            if (!enabled) android.os.SystemClock.sleep(20)
        }
        assertTrue("Save button did not become available", enabled)
    }

    @Test
    fun debriefFeedbackAndPrepSaveToTheExistingInterviewAndSurviveRotation() = runBlocking {
        val original =
            Interview(
                jobTitle = "Engineer",
                companyName = "Example",
                stage = InterviewStage.FIRST_STAGE,
                applicationDate = LocalDate.of(2026, 1, 1),
                interviewInstant = Instant.parse("2026-01-02T10:00:00Z"),
                parentId = 4,
                applicationId = 7,
                debriefJSON =
                    StageReflection.debrief(
                        mapOf("debriefNotes" to "Original reflection"),
                        null,
                        null,
                    ),
                feedbackJSON = StageReflection.feedback(null, ""),
                prepJSON = StageReflection.prep("Old prep", emptyList()),
            )
        id = app.database.interviewDao().insert(original)
        val intent =
            Intent(context, StageReflectionActivity::class.java)
                .putExtra(StageReflectionActivity.EXTRA_INTERVIEW_ID, id)
        ActivityScenario.launch<StageReflectionActivity>(intent).use { scenario ->
            awaitText(scenario, R.id.editDebriefNotes, "Original reflection")
            scenario.onActivity {
                it.findViewById<TextInputEditText>(R.id.editDebriefNotes)
                    .setText("Fresh reflection")
            }
            scenario.recreate()
            awaitText(scenario, R.id.editDebriefNotes, "Fresh reflection")
            enabled(scenario, R.id.buttonSaveDebrief)
            scenario.onActivity { activity ->
                activity
                    .findViewById<TextInputEditText>(R.id.editQuestionsAsked)
                    .setText("Explain indexes")
                val rating = activity.findViewById<AutoCompleteTextView>(R.id.dropdownRating)
                rating.onItemClickListener.onItemClick(null, rating, 3, 0)
                activity.findViewById<android.view.View>(R.id.buttonSaveDebrief).performClick()
            }
            withTimeout(10000) {
                while (
                    app.database.interviewDao().getInterviewById(id)!!.debriefedAt == null
                ) delay(20)
            }
            enabled(scenario, R.id.buttonSaveFeedback)
            scenario.onActivity { activity ->
                val feedback = activity.findViewById<AutoCompleteTextView>(R.id.dropdownFeedback)
                feedback.onItemClickListener.onItemClick(null, feedback, 3, 0)
                activity
                    .findViewById<TextInputEditText>(R.id.editFeedbackText)
                    .setText("More SQL experience")
                activity.findViewById<android.view.View>(R.id.buttonSaveFeedback).performClick()
            }
            withTimeout(10000) {
                while (
                    !app.database
                        .interviewDao()
                        .getInterviewById(id)!!
                        .feedbackJSON
                        .orEmpty()
                        .contains("SPECIFIC")
                ) delay(20)
            }
            enabled(scenario, R.id.buttonSavePrep)
            scenario.onActivity { activity ->
                activity
                    .findViewById<TextInputEditText>(R.id.editPrepNotes)
                    .setText("Practice SQL examples")
                activity.findViewById<android.view.View>(R.id.buttonSavePrep).performClick()
            }
            withTimeout(10000) {
                while (
                    app.database.interviewDao().getInterviewById(id)!!.pendingPrepJSON == null
                ) delay(20)
            }
            val saved = app.database.interviewDao().getInterviewById(id)!!
            assertEquals(original.parentId, saved.parentId)
            assertEquals(original.applicationId, saved.applicationId)
            assertNull(saved.serverId)
            val patch = JsonParser.parseString(saved.pendingChanges).asJsonObject
            assertEquals(
                "Fresh reflection",
                patch.getAsJsonObject("debrief").get("debriefNotes").asString,
            )
            assertEquals(3, patch.getAsJsonObject("debrief").get("rating").asInt)
            assertEquals(
                "Explain indexes",
                patch.getAsJsonObject("debrief").get("questionsAsked").asString,
            )
            assertFalse(patch.getAsJsonObject("debrief").has("prepNotes"))
            assertEquals("SPECIFIC", patch.getAsJsonObject("feedback").get("type").asString)
            assertEquals(
                "Practice SQL examples",
                JsonParser.parseString(saved.pendingPrepJSON).asJsonObject.get("notes").asString,
            )
        }
    }
}
