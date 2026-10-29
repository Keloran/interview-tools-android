package tools.interviews.android

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.data.api.*
import tools.interviews.android.model.Interview
import tools.interviews.android.util.finishOnAccountChange

class StageReflectionActivity : AppCompatActivity() {
    private val app
        get() = application as InterviewApplication

    private lateinit var screenOwner: String
    private var row: Interview? = null
    private var id: Long = -1
    private lateinit var status: TextView
    private lateinit var saveDebrief: MaterialButton
    private lateinit var saveFeedback: MaterialButton
    private lateinit var savePrep: MaterialButton
    private lateinit var refresh: MaterialButton
    private val fields =
        mapOf(
            "debriefNotes" to R.id.editDebriefNotes,
            "peopleMet" to R.id.editPeopleMet,
            "questionsAsked" to R.id.editQuestionsAsked,
            "nextStep" to R.id.editNextStep,
        )
    private val selectedStories = linkedSetOf<Int>()
    private var stories = JsonArray()
    private var feedbackType: String? = null
    private var rating: Int? = null
    private var restored: String? = null

    companion object {
        const val EXTRA_INTERVIEW_ID = "interview_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        screenOwner = app.ownerNow()
        if (savedInstanceState?.getString("owner")?.let { it != screenOwner } == true) {
            finish()
            return
        }
        setContentView(R.layout.activity_stage_reflection)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        id = intent.getLongExtra(EXTRA_INTERVIEW_ID, -1)
        if (
            id < 0 || intent.getStringExtra("expected_owner")?.let { it != app.ownerNow() } == true
        ) {
            finish()
            return
        }
        status = findViewById(R.id.textReflectionStatus)
        saveDebrief = findViewById(R.id.buttonSaveDebrief)
        saveFeedback = findViewById(R.id.buttonSaveFeedback)
        savePrep = findViewById(R.id.buttonSavePrep)
        refresh = findViewById(R.id.buttonReflectionRefresh)
        saveDebrief.setOnClickListener { save("debrief") }
        saveFeedback.setOnClickListener { save("feedback") }
        savePrep.setOnClickListener { save("prep") }
        refresh.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage("Refresh saved details and replace the fields shown here?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Refresh") { _, _ -> load() }
                .show()
        }
        findViewById<MaterialButton>(R.id.buttonBrowseQuestions).setOnClickListener {
            bank("questions")
        }
        findViewById<MaterialButton>(R.id.buttonManageStories).setOnClickListener {
            bank("stories")
        }
        if (savedInstanceState?.getString("owner") == app.ownerNow())
            restored = savedInstanceState.getString("fields")
        load()
    }

    private fun field(id: Int) = findViewById<TextInputEditText>(id)

    private fun text(id: Int) = field(id).text.toString()

    private fun bank(mode: String) {
        startActivity(
            Intent(this, InterviewBankActivity::class.java)
                .putExtra("mode", mode)
                .putExtra("expected_owner", screenOwner)
        )
    }

    private fun setEnabled(value: Interview?) {
        saveDebrief.isEnabled =
            value != null && (value.serverId == null || value.debriefJSON != null)
        saveFeedback.isEnabled =
            value != null && (value.serverId == null || value.feedbackJSON != null)
        savePrep.isEnabled = value != null && (value.serverId == null || value.prepJSON != null)
    }

    private fun load() {
        setEnabled(null)
        refresh.isEnabled = false
        status.text = "Loading saved interview details…"
        lifecycleScope.launch {
            try {
                row =
                    app.repository.getById(id)
                        ?: run {
                            finish()
                            return@launch
                        }
                val original = row!!
                if (original.serverId != null && original.ownerId != AccountOwners.GUEST) {
                    try {
                        app.reflectionStore.refresh(original.ownerId, id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        status.text =
                            "Couldn’t refresh. Previously loaded fields remain available offline; connect once to load missing details."
                    }
                    row =
                        app.repository.getById(id)
                            ?: run {
                                finish()
                                return@launch
                            }
                }
                populate(row!!)
                restored?.let {
                    restoreFields(it)
                    restored = null
                }
                setEnabled(row)
                if (status.text == "Loading saved interview details…")
                    status.text = "Debrief, feedback and preparation are private to your account."
                loadStories()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "Couldn’t load this interview."
            } finally {
                refresh.isEnabled = true
            }
        }
    }

    private fun obj(json: String?) =
        json?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()

    private fun JsonObject.value(key: String) =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun populate(value: Interview) {
        val debrief = obj(value.debriefJSON)
        for ((key, id) in fields) field(id).setText(debrief.value(key))
        field(R.id.editReplyBy).setText(debrief.value("replyBy").take(10))
        val dropdown = findViewById<AutoCompleteTextView>(R.id.dropdownRating)
        val ratings = listOf("Unknown", "1", "2", "3", "4", "5")
        rating = debrief.value("rating").toIntOrNull()
        dropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ratings)
        )
        dropdown.setText(rating?.toString() ?: "Unknown", false)
        dropdown.setOnItemClickListener { _, _, position, _ -> rating = position.takeIf { it > 0 } }
        val feedback = obj(value.feedbackJSON)
        feedbackType = feedback.value("type").takeIf { it.isNotBlank() }
        val feedbackDropdown = findViewById<AutoCompleteTextView>(R.id.dropdownFeedback)
        val labels = listOf("Not recorded", "No feedback", "Generic feedback", "Specific feedback")
        feedbackDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        )
        feedbackDropdown.setText(
            when (feedbackType) {
                null -> labels[0]
                "NONE" -> labels[1]
                "GENERIC" -> labels[2]
                "SPECIFIC" -> labels[3]
                else -> "Other feedback"
            },
            false,
        )
        feedbackDropdown.setOnItemClickListener { _, _, position, _ ->
            feedbackType = listOf(null, "NONE", "GENERIC", "SPECIFIC")[position]
        }
        field(R.id.editFeedbackText).setText(feedback.value("text"))
        val prep = obj(value.prepJSON)
        field(R.id.editPrepNotes).setText(prep.value("notes"))
        selectedStories.clear()
        prep.getAsJsonArray("storyIds")?.forEach { selectedStories += it.asInt }
        renderStories()
    }

    private suspend fun loadStories() {
        val owner = row?.ownerId ?: return
        if (owner == AccountOwners.GUEST) return
        try {
            stories = app.authenticatedRequests.run(owner) { APIService.getInstance().stories() }
            renderStories()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            /* Existing selected IDs remain visible and can be preserved/removed. */
        }
    }

    override fun onResume() {
        super.onResume()
        if (row != null) lifecycleScope.launch { loadStories() }
    }

    private fun renderStories() {
        val parent = findViewById<LinearLayout>(R.id.storyChoices)
        parent.removeAllViews()
        val available =
            stories.map { it.asJsonObject }.associate { it.get("id").asInt to it.value("title") }
        for (storyId in (available.keys + selectedStories).distinct()) {
            parent.addView(
                MaterialCheckBox(this).apply {
                    text = available[storyId] ?: "Unavailable story #$storyId"
                    isChecked = storyId in selectedStories
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedStories += storyId else selectedStories -= storyId
                    }
                }
            )
        }
    }

    private fun save(kind: String) {
        val original = row ?: return
        val changed =
            try {
                when (kind) {
                    "debrief" ->
                        original.copy(
                            debriefJSON =
                                StageReflection.debrief(
                                    fields.mapValues { text(it.value) },
                                    rating,
                                    text(R.id.editReplyBy).trim(),
                                ),
                            debriefedAt = Instant.now().toString(),
                        )
                    "feedback" ->
                        original.copy(
                            feedbackJSON =
                                StageReflection.feedback(feedbackType, text(R.id.editFeedbackText))
                        )
                    else ->
                        original.copy(
                            prepJSON =
                                StageReflection.prep(text(R.id.editPrepNotes), selectedStories)
                        )
                }
            } catch (error: IllegalArgumentException) {
                status.text = error.message
                return
            }
        setEnabled(null)
        lifecycleScope.launch {
            try {
                app.repository.saveChanges(original, changed)
                row = app.repository.getById(id)
                app.schedulePendingSync(original.ownerId)
                status.text =
                    if (original.ownerId == AccountOwners.GUEST) "Saved on this device."
                    else "Saved on this device; waiting to sync."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "Couldn’t save these fields."
            } finally {
                setEnabled(row)
            }
        }
    }

    private fun state() =
        JsonObject()
            .apply {
                fields.forEach { (key, id) -> addProperty(key, text(id)) }
                addProperty("replyBy", text(R.id.editReplyBy))
                addProperty("rating", rating)
                addProperty("feedbackType", feedbackType)
                addProperty("feedbackText", text(R.id.editFeedbackText))
                addProperty("prepNotes", text(R.id.editPrepNotes))
                add("storyIds", JsonArray().apply { selectedStories.forEach { add(it) } })
            }
            .toString()

    private fun restoreFields(json: String) {
        val saved = obj(json)
        fields.forEach { (key, id) -> field(id).setText(saved.value(key)) }
        field(R.id.editReplyBy).setText(saved.value("replyBy"))
        rating = saved.value("rating").toIntOrNull()
        findViewById<AutoCompleteTextView>(R.id.dropdownRating)
            .setText(rating?.toString() ?: "Unknown", false)
        feedbackType = saved.value("feedbackType").takeIf { it.isNotBlank() }
        findViewById<AutoCompleteTextView>(R.id.dropdownFeedback)
            .setText(
                when (feedbackType) {
                    null -> "Not recorded"
                    "NONE" -> "No feedback"
                    "GENERIC" -> "Generic feedback"
                    "SPECIFIC" -> "Specific feedback"
                    else -> "Other feedback"
                },
                false,
            )
        field(R.id.editFeedbackText).setText(saved.value("feedbackText"))
        field(R.id.editPrepNotes).setText(saved.value("prepNotes"))
        selectedStories.clear()
        saved.getAsJsonArray("storyIds")?.forEach { selectedStories += it.asInt }
        renderStories()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::status.isInitialized) return
        outState.putString("owner", screenOwner)
        outState.putString("fields", state())
    }
}
