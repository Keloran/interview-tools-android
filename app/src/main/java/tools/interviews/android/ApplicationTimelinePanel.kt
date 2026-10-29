package tools.interviews.android

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.data.api.ApplicationTimeline
import tools.interviews.android.data.api.timelineDate
import tools.interviews.android.model.Interview
import java.time.Instant
import java.time.ZoneOffset

/** Native Material controls; stale account responses never reach the visible panel. */
class ApplicationTimelinePanel(private val activity: AppCompatActivity) {
    private val app get() = activity.application as InterviewApplication
    private val content = activity.findViewById<LinearLayout>(R.id.timelineEvents)
    private val status = activity.findViewById<TextView>(R.id.textTimelineStatus)
    private val reply = activity.findViewById<TextView>(R.id.textFirstReply)
    private val change = activity.findViewById<MaterialButton>(R.id.buttonFirstReply)
    private val refresh = activity.findViewById<MaterialButton>(R.id.buttonTimelineRefresh)
    private var selected: Interview? = null
    private var applicationId: Int? = null
    private var owner: String? = null
    private var job: Job? = null
    init {
        refresh.setOnClickListener { selected?.let { load(it) } }
        change.setOnClickListener {
            MaterialAlertDialogBuilder(activity).setTitle("First reply")
                .setItems(arrayOf("Choose known reply date", "Clear date (unknown)")) { _, position ->
                    if (position == 1) save(null) else {
                        val picker = MaterialDatePicker.Builder.datePicker().setTitleText("First reply date").build()
                        picker.addOnPositiveButtonClickListener { millis -> save(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
                        picker.show(activity.supportFragmentManager, "first-reply")
                    }
                }.show()
        }
    }
    fun bind(row: Interview) {
        selected = row
        if (applicationId == row.applicationId && owner == row.ownerId) return
        applicationId = row.applicationId; owner = row.ownerId
        load(row)
    }
    private fun current(row: Interview) = selected?.ownerId == row.ownerId && selected?.applicationId == row.applicationId && app.ownerNow() == row.ownerId
    private fun load(row: Interview) {
        job?.cancel()
        content.removeAllViews()
        reply.text = "First reply: Unknown"
        val id = row.applicationId
        val online = id != null && row.ownerId != AccountOwners.GUEST
        change.isEnabled = online; refresh.isEnabled = online
        if (!online) {
            status.text = "The application timeline is available after this draft syncs to your account."
            return
        }
        job = activity.lifecycleScope.launch {
            val history = app.applicationHistory
            try {
                val cached = history.cached(row.ownerId, id)
                if (!current(row)) return@launch
                cached?.let { render(it) }
                status.text = if (cached == null) "Loading application…" else "Saved timeline · refreshing…"
                val result = history.refresh(row.ownerId, id)
                if (current(row)) { render(result); status.text = "Application timeline" }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(row)) status.text = "Couldn’t refresh the timeline. Saved history remains available. Tap Refresh to retry." }
        }
    }
    private fun render(value: ApplicationTimeline) {
        reply.text = "First reply: ${value.firstResponseAt?.take(10) ?: "Unknown"}"
        content.removeAllViews()
        for (event in value.events) {
            content.addView(TextView(activity).apply {
                text = "${event.title}\n${if (event.dateOnly) event.date.take(10) else timelineDate(event.date)}\n${event.detail}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(0, 12, 0, 16)
                setTextIsSelectable(true)
            })
        }
    }
    private fun save(date: java.time.LocalDate?) {
        val row = selected ?: return
        val id = row.applicationId ?: return
        change.isEnabled = false
        activity.lifecycleScope.launch {
            try {
                val value = app.applicationHistory.saveFirstReply(row.ownerId, id, date, row.applicationDate)
                if (current(row)) {
                    reply.text = "First reply: ${value?.take(10) ?: "Unknown"}"
                    status.text = "First-reply date saved"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                if (current(row)) status.text = "First-reply date wasn’t saved. ${e.message ?: "Check your connection and try again."}"
            } finally { if (current(row)) change.isEnabled = true }
        }
    }
}
