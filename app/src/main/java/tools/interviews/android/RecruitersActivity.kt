package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.interviews.android.data.api.*
import tools.interviews.android.util.finishOnAccountChange

class RecruitersActivity : AppCompatActivity() {
    private val app get() = application as InterviewApplication
    private lateinit var records: LinearLayout
    private lateinit var status: TextView
    private lateinit var refresh: MaterialButton
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); finishOnAccountChange()
        setContentView(R.layout.activity_recruiters)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        records = findViewById(R.id.recruiterRecords); status = findViewById(R.id.textRecruiterStatus)
        refresh = findViewById(R.id.buttonRefreshRecruiters)
        refresh.setOnClickListener { load() }; load()
    }
    private fun load() {
        val owner = app.ownerNow()
        refresh.isEnabled = false; status.text = "Loading your recruiter records…"; records.removeAllViews()
        lifecycleScope.launch {
            try {
                val result = app.authenticatedRequests.run(owner) { APIService.getInstance().recruiters() }
                val values = RecruiterRecord.parse(result)
                status.text = if (values.isEmpty()) "No recruiter records yet. Record agency details on your applications." else "${values.size} agency/recruiter records"
                values.forEach { render(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status.text = "Couldn’t load recruiters. ${error.message ?: "Check your connection and retry."}" }
            finally { refresh.isEnabled = true }
        }
    }
    private fun render(record: RecruiterRecord) {
        val card = MaterialCardView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        content.addView(TextView(this).apply { text = record.contact?.name?.takeIf { it.isNotBlank() }?.let { "${record.agency} · $it" } ?: record.agency; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium) })
        content.addView(TextView(this).apply { text = "${record.roles} roles · ${record.ghosted} ghosted\nReply rate ${record.responseRate} · Ghost rate ${record.ghostRate}\nFeedback rate ${record.feedbackRate}"; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge) })
        val contact = record.contact
        if (contact != null) {
            if (contact.email.isNotBlank() && runCatching { contact.copy(viaRecruiter=true, linkedIn="").json() }.isSuccess) button(content, "Email ${contact.email}", Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", contact.email, null)))
            if (RecruiterContact.safeLinkedIn(contact.linkedIn)) button(content, "LinkedIn profile", Intent(Intent.ACTION_VIEW, Uri.parse(contact.linkedIn)))
        }
        card.addView(content); records.addView(card, LinearLayout.LayoutParams(-1,-2).apply { topMargin=16 })
    }
    private fun button(parent: LinearLayout, label: String, intent: Intent) {
        parent.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text=label; setOnClickListener { runCatching { startActivity(intent) }.onFailure { status.text="No app is available to open this contact." } }
        })
    }
}
