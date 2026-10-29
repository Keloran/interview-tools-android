package tools.interviews.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.interviews.android.data.api.*
import tools.interviews.android.reminders.FollowUpReminders
import tools.interviews.android.util.finishOnAccountChange

class FollowUpsActivity : AppCompatActivity() {
    private val app
        get() = application as InterviewApplication

    private lateinit var owner: String
    private lateinit var status: TextView
    private lateinit var entries: LinearLayout
    private lateinit var refresh: MaterialButton
    private var loading = false
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) FollowUpReminders.schedule(this, owner)
            else status.text = "Notifications are off. Due follow-ups remain available here."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        owner = app.ownerNow()
        if (intent.getStringExtra("expected_owner")?.let { it != owner } == true) {
            finish()
            return
        }
        setContentView(R.layout.activity_follow_ups)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        status = findViewById(R.id.textFollowUpStatus)
        entries = findViewById(R.id.followUpEntries)
        refresh = findViewById(R.id.buttonRefreshFollowUps)
        refresh.setOnClickListener { load() }
        findViewById<MaterialButton>(R.id.buttonEnableFollowUpReminders).isEnabled =
            owner != tools.interviews.android.data.account.AccountOwners.GUEST
        findViewById<MaterialButton>(R.id.buttonEnableFollowUpReminders).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Follow-up reminders")
                .setMessage("Get a reminder when your recorded application is due a follow-up.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Enable") { _, _ ->
                    if (
                        Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    )
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else FollowUpReminders.schedule(this, owner)
                }
                .show()
        }
        load()
    }

    private fun JsonObject.value(key: String) =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun heading(text: String) {
        entries.addView(
            TextView(this).apply {
                this.text = text
                setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
                )
                setPadding(0, 24, 0, 8)
            }
        )
    }

    private fun load() {
        if (loading) return
        loading = true
        refresh.isEnabled = false
        status.text = "Refreshing follow-ups…"
        entries.removeAllViews()
        lifecycleScope.launch {
            var cachedFollowUps: JsonArray? = null
            var cachedStale: JsonObject? = null
            try {
                cachedFollowUps =
                    app.toolDrafts.get(owner, "follow-ups:cache")?.let {
                        JsonParser.parseString(it).asJsonArray
                    }
                cachedStale =
                    app.toolDrafts.get(owner, "stale:cache")?.let {
                        JsonParser.parseString(it).asJsonObject
                    }
                val states = app.followUpQueue.states(owner)
                if (cachedFollowUps != null || cachedStale != null)
                    render(
                        cachedFollowUps ?: JsonArray(),
                        cachedStale ?: JsonObject(),
                        states,
                        false,
                    )
                val failed = app.followUpQueue.push(owner)
                val due =
                    app.authenticatedRequests.run(owner) { APIService.getInstance().followUps() }
                val stale =
                    app.authenticatedRequests.run(owner) {
                        APIService.getInstance().staleApplications()
                    }
                if (due.toString().length <= 500000)
                    app.toolDrafts.save(owner, "follow-ups:cache", due.toString())
                if (stale.toString().length <= 500000)
                    app.toolDrafts.save(owner, "stale:cache", stale.toString())
                render(due, stale, app.followUpQueue.states(owner), true)
                status.text =
                    if (failed > 0)
                        "Some recorded follow-ups need retry. They remain saved on this device."
                    else "Due follow-ups and quiet applications."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text =
                    "Couldn’t refresh. ${error.message ?: "Saved entries remain available offline."}"
            } finally {
                loading = false
                refresh.isEnabled = true
            }
        }
    }

    private fun card(title: String, detail: String): LinearLayout {
        val card = MaterialCardView(this)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }
        content.addView(
            TextView(this).apply {
                text = title
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
        card.addView(content)
        entries.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        return content
    }

    private fun button(
        parent: LinearLayout,
        label: String,
        enabled: Boolean = true,
        action: (MaterialButton) -> Unit,
    ) {
        parent.addView(
            MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
                .apply {
                    text = label
                    isEnabled = enabled
                    setOnClickListener { action(this) }
                }
        )
    }

    private fun render(
        due: JsonArray,
        stale: JsonObject,
        states: Map<Int, JsonObject>,
        fresh: Boolean,
    ) {
        entries.removeAllViews()
        heading("Due follow-ups")
        if (due.isEmpty)
            card(
                "No due follow-ups in this view",
                "Refresh while connected to check the latest dates.",
            )
        for (element in due) {
            val record = element.asJsonObject
            val id = record.get("id").asInt
            val dueAt = record.value("dueAt")
            val state = states[id]
            val pending = state?.get("pending")?.asBoolean == true
            val acknowledged = state?.value("dueAt") == dueAt && !pending
            val detail =
                listOf(
                        "${record.value("stage")} · Due ${dueAt.take(10)}",
                        record
                            .value("replyBy")
                            .takeIf { it.isNotBlank() }
                            ?.let { "They said they’d reply by ${it.take(10)}" }
                            .orEmpty(),
                        "${record.value("followUps")} follow-up(s) recorded",
                        if (pending)
                            state?.value("error")?.takeIf { it.isNotBlank() }
                                ?: "Follow-up recorded on this device; waiting to sync"
                        else if (acknowledged) "Follow-up already recorded" else "",
                    )
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            val content = card("${record.value("company")} · ${record.value("jobTitle")}", detail)
            val context =
                MessageContext(
                    record.value("jobTitle"),
                    record.value("company"),
                    record.value("clientCompany").takeIf { it.isNotBlank() },
                    record.value("stage"),
                    record.value("interviewer"),
                    runCatching { Instant.parse(record.value("date")) }.getOrNull(),
                )
            button(content, "Message template") {
                MessageComposer.choose(
                    this,
                    owner,
                    context,
                    MessageTemplates.available("AWAITING_RESPONSE", context.stage, context.date)
                        .filter {
                            it == MessageTemplate.FOLLOW_UP || it == MessageTemplate.THANK_YOU
                        },
                )
            }
            button(
                content,
                if (pending) "Waiting to sync"
                else if (acknowledged) "Recorded" else "Record followed up",
                !pending && !acknowledged,
            ) { clicked ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Record a follow-up?")
                    .setMessage(
                        "Record that you have already contacted this employer about the role."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Record") { _, _ ->
                        clicked.isEnabled = false
                        lifecycleScope.launch {
                            try {
                                app.followUpQueue.enqueue(owner, id, dueAt)
                                status.text = "Follow-up recorded on this device."
                                app.scheduleFollowUpSync(owner)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status.text = error.message
                                clicked.isEnabled = true
                            }
                        }
                    }
                    .show()
            }
        }
        heading("Quiet applications")
        val days = stale.get("days")?.asInt ?: 60
        val candidates = stale.getAsJsonArray("candidates") ?: JsonArray()
        if (candidates.isEmpty)
            card(
                "No quiet applications in this view",
                "Applications are rechecked before they are closed.",
            )
        for (element in candidates) {
            val record = element.asJsonObject
            val id = record.get("id").asInt
            val content =
                card(
                    "${record.value("company")} · ${record.value("jobTitle")}",
                    "${record.value("lastStage")} · ${record.value("quietDays")} days quiet\nLast activity ${record.value("lastActivity").take(10)}",
                )
            button(
                content,
                if (fresh) "Close as ghosted" else "Refresh to close as ghosted",
                fresh,
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Close as ghosted?")
                    .setMessage(
                        "Record No response for this application after at least $days days without activity. Recent activity is checked again before closure."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Close as ghosted") { _, _ -> close(id, days) }
                    .show()
            }
        }
    }

    private fun close(id: Int, days: Int) {
        lifecycleScope.launch {
            try {
                val body =
                    JsonObject().apply {
                        add("ids", JsonArray().apply { add(id) })
                        addProperty("days", days)
                        addProperty("confirm", true)
                    }
                val response =
                    app.authenticatedRequests.run(owner) {
                        val local = app.database.interviewDao().forApplication(id, owner)
                        check(
                            local.none {
                                it.serverId == null ||
                                    it.pendingChanges != null ||
                                    it.pendingRecruiterJSON != null ||
                                    it.pendingPrepJSON != null
                            }
                        ) {
                            "Sync your pending edits on this application before closing it."
                        }
                        APIService.getInstance().closeStale(body)
                    }
                val closed = response.getAsJsonArray("closed")?.any { it.asInt == id } == true
                val message =
                    if (closed) "Application closed as ghosted."
                    else
                        "This application changed or is no longer stale. The latest records have been kept."
                com.google.android.material.snackbar.Snackbar.make(
                        entries,
                        message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                    )
                    .show()
                if (closed) app.scheduleFullSync()
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "Couldn’t close this application. Refresh and retry."
            }
        }
    }
}
