package tools.interviews.android

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.data.api.APIService
import tools.interviews.android.util.finishOnAccountChange

class InterviewBankActivity : AppCompatActivity() {
    private val app
        get() = application as InterviewApplication

    private lateinit var status: TextView
    private lateinit var entries: LinearLayout
    private lateinit var search: TextInputEditText
    private lateinit var screenOwner: String
    private var mode = "questions"
    private var data = JsonArray()
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        screenOwner = app.ownerNow()
        if (
            (savedInstanceState?.getString("owner")?.let { it != screenOwner } == true) ||
                (intent.getStringExtra("expected_owner")?.let { it != screenOwner } == true)
        ) {
            finish()
            return
        }
        setContentView(R.layout.activity_interview_bank)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        status = findViewById(R.id.textBankStatus)
        entries = findViewById(R.id.bankEntries)
        search = findViewById(R.id.editBankSearch)
        mode = savedInstanceState?.getString("mode") ?: intent.getStringExtra("mode") ?: "questions"
        findViewById<MaterialButton>(R.id.buttonBankQuestions).setOnClickListener {
            mode = "questions"
            load()
        }
        findViewById<MaterialButton>(R.id.buttonBankStories).setOnClickListener {
            mode = "stories"
            load()
        }
        findViewById<MaterialButton>(R.id.buttonBankRefresh).setOnClickListener { load() }
        findViewById<MaterialButton>(R.id.buttonBankNewStory).apply {
            isEnabled = app.ownerNow() != AccountOwners.GUEST
            setOnClickListener {
                startActivity(
                    Intent(this@InterviewBankActivity, StoryEditorActivity::class.java)
                        .putExtra("expected_owner", screenOwner)
                )
            }
        }
        search.doAfterTextChanged { render() }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) load()
    }

    private fun load() {
        job?.cancel()
        val selected = mode
        val owner = screenOwner
        data = JsonArray()
        entries.removeAllViews()
        status.text = "Loading your $selected…"
        job = lifecycleScope.launch {
            try {
                val cached = app.toolDrafts.get(owner, "bank:$selected")
                if (cached != null) {
                    data = JsonParser.parseString(cached).asJsonArray
                    render()
                    status.text = "Saved $selected · refreshing…"
                }
                val response =
                    app.authenticatedRequests.run(owner) {
                        if (selected == "stories") APIService.getInstance().stories()
                        else APIService.getInstance().questions()
                    }
                if (mode != selected) return@launch
                val serialized = response.toString()
                // Keep each cache row below Android CursorWindow's per-row limit.
                if (serialized.length <= 500_000)
                    app.toolDrafts.save(owner, "bank:$selected", serialized)
                else app.toolDrafts.delete(owner, "bank:$selected")
                data = response
                render()
                status.text = if (data.isEmpty) "No $selected recorded yet." else "Your $selected"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mode == selected)
                    status.text =
                        "Couldn’t refresh $selected. ${error.message ?: "Saved entries remain available; check your connection and retry."}"
            }
        }
    }

    private fun JsonObject.value(key: String) =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun searchable(value: JsonElement): String =
        when {
            value.isJsonNull -> ""
            value.isJsonPrimitive -> value.asString.lowercase()
            value.isJsonArray -> value.asJsonArray.joinToString(" ") { searchable(it) }
            else -> value.asJsonObject.entrySet().joinToString(" ") { searchable(it.value) }
        }

    private fun render() {
        entries.removeAllViews()
        val query = search.text.toString().trim().lowercase()
        for (item in data) {
            val entry = item.asJsonObject
            if (query.isNotBlank() && !searchable(entry).contains(query)) continue
            val heading =
                if (mode == "stories") entry.value("title")
                else "${entry.value("company")} · ${entry.value("stage")}"
            val detail =
                if (mode == "stories")
                    listOf("situation", "task", "action", "result").joinToString("\n") {
                        "${it.replaceFirstChar(Char::uppercase)}: ${entry.value(it)}"
                    }
                else
                    entry.getAsJsonArray("questions").joinToString("\n") { it.asString } +
                        "\n" +
                        entry.value("notes")
            val card = MaterialCardView(this)
            val content =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                }
            content.addView(
                TextView(this).apply {
                    text = heading
                    setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
                    )
                }
            )
            content.addView(
                TextView(this).apply {
                    text = detail
                    setTextIsSelectable(true)
                }
            )
            if (mode == "stories")
                content.addView(
                    MaterialButton(
                            this,
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle,
                        )
                        .apply {
                            text = "Edit story"
                            setOnClickListener {
                                startActivity(
                                    Intent(
                                            this@InterviewBankActivity,
                                            StoryEditorActivity::class.java,
                                        )
                                        .putExtra("expected_owner", screenOwner)
                                        .putExtra("story_json", entry.toString())
                                        .putExtra("story_id", entry.get("id").asInt)
                                )
                            }
                        }
                )
            card.addView(content)
            entries.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("mode", mode)
        outState.putString("owner", screenOwner)
    }
}
