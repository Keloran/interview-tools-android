package tools.interviews.android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.interviews.android.data.api.StoryFields
import tools.interviews.android.util.finishOnAccountChange

class StoryEditorActivity : AppCompatActivity() {
    private val app
        get() = application as InterviewApplication

    private lateinit var owner: String
    private var storyId: Int? = null
    private lateinit var key: String
    private lateinit var status: TextView
    private lateinit var save: MaterialButton
    private val fields =
        mapOf(
            "title" to R.id.editStoryTitle,
            "situation" to R.id.editStorySituation,
            "task" to R.id.editStoryTask,
            "action" to R.id.editStoryAction,
            "result" to R.id.editStoryResult,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        owner = app.ownerNow()
        if (
            (savedInstanceState?.getString("owner")?.let { it != owner } == true) ||
                (intent.getStringExtra("expected_owner")?.let { it != owner } == true)
        ) {
            finish()
            return
        }
        setContentView(R.layout.activity_story_editor)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        status = findViewById(R.id.textStoryStatus)
        save = findViewById(R.id.buttonSaveStory)
        storyId = intent.getIntExtra("story_id", 0).takeIf { it > 0 }
        key = "story:${storyId ?: "new"}"
        save.isEnabled = false
        lifecycleScope.launch {
            try {
                val draft =
                    app.toolDrafts.get(owner, key)?.let {
                        JsonParser.parseString(it).asJsonObject.getAsJsonObject("body")
                    }
                val restored =
                    if (savedInstanceState?.getString("owner") == owner)
                        savedInstanceState.getString("body")
                    else null
                val json =
                    restored?.let { JsonParser.parseString(it).asJsonObject }
                        ?: draft
                        ?: intent.getStringExtra("story_json")?.let {
                            JsonParser.parseString(it).asJsonObject
                        }
                        ?: JsonObject()
                populate(json)
                status.text =
                    if (draft != null) "Unsynced draft restored. Tap Save to retry."
                    else "Build a story with Situation, Task, Action and Result."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "Couldn’t load this story draft."
            } finally {
                save.isEnabled = true
            }
        }
        save.setOnClickListener { saveStory() }
    }

    private fun field(id: Int) = findViewById<TextInputEditText>(id)

    private fun JsonObject.value(key: String) =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun populate(json: JsonObject) {
        fields.forEach { (name, id) -> field(id).setText(json.value(name)) }
        field(R.id.editStoryTags)
            .setText(json.getAsJsonArray("tags")?.joinToString(", ") { it.asString }.orEmpty())
    }

    private fun body() =
        StoryFields.body(
            fields.mapValues { field(it.value).text.toString() },
            field(R.id.editStoryTags).text.toString(),
        )

    private fun enabled(value: Boolean) {
        save.isEnabled = value
        fields.values.forEach { field(it).isEnabled = value }
        field(R.id.editStoryTags).isEnabled = value
    }

    private fun saveStory() {
        val json =
            try {
                body()
            } catch (error: IllegalArgumentException) {
                status.text = error.message
                return
            }
        enabled(false)
        status.text = "Saving story…"
        lifecycleScope.launch {
            try {
                app.storySaver.save(owner, key, storyId, json)
                status.text = "Story saved."
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text =
                    "Couldn’t finish saving. ${error.message ?: "Keep this draft open and tap Save to retry."}"
            } finally {
                enabled(true)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::save.isInitialized) return
        outState.putString("owner", owner)
        val snapshot =
            JsonObject().apply {
                fields.forEach { (name, id) -> addProperty(name, field(id).text.toString()) }
                add(
                    "tags",
                    JsonArray().apply {
                        field(R.id.editStoryTags).text.toString().split(',').forEach {
                            add(it.trim())
                        }
                    },
                )
            }
        outState.putString("body", snapshot.toString())
    }
}
